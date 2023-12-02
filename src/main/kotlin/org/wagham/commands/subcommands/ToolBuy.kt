package org.wagham.commands.subcommands

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.mongodb.reactivestreams.client.ClientSession
import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import kotlinx.coroutines.flow.*
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.ToolCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.ToolBuyLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.ScheduledEventArg
import org.wagham.db.enums.ScheduledEventState
import org.wagham.db.enums.ScheduledEventType
import org.wagham.db.enums.TransactionType
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Character
import org.wagham.db.models.ScheduledEvent
import org.wagham.db.models.ToolProficiency
import org.wagham.db.models.embed.AbilityCost
import org.wagham.db.models.embed.ProficiencyStub
import org.wagham.db.models.embed.Transaction
import org.wagham.utils.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.IllegalStateException
import kotlin.time.Duration.Companion.days

@BotSubcommand("wagham", ToolCommand::class)
class ToolBuy(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    companion object {
        private data class AssignToolInteractionData(
            val responsible: Snowflake,
            val character: Character,
            val tool: ToolProficiency
        )
    }

    override val commandName = "buy"
    private val dateFormatter = SimpleDateFormat("dd/MM/YYYY HH:mm:ss")
    override val defaultDescription = ToolBuyLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = ToolBuyLocale.DESCRIPTION.localeMap
    private val interactionCache: Cache<String, AssignToolInteractionData> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build()
    private val delaysPerToolNumber = listOf(0.days, 3.days, 3.days, 7.days, 7.days, 14.days, 14.days, 21.days, 21.days, 28.days)
    private val costPerToolNumber = listOf(150f, 300f, 300f, 750f, 750f, 1500f, 1500f, 3000f, 3000f, 6000f)

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("proficiency", ToolBuyLocale.PROFICIENCY.locale("en")) {
            ToolBuyLocale.PROFICIENCY.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
    }

    override suspend fun registerCommand() {
        kord.on<ButtonInteractionCreateEvent> {
            if(verifyId(interaction.componentId)) {
                val params = interaction.extractCommonParameters()
                val (id) = extractComponentsFromComponentId(interaction.componentId)
                val data = interactionCache.getIfPresent(id)
                when {
                    data == null -> interaction.updateWithExpirationError(params.locale)
                    data.responsible != params.responsible.id -> interaction.respondWithForbiddenError(params.locale)
                    else -> checkRequirementsAndBuyTool(params.guildId.toString(), data.tool, data.character, params.locale).let {
                        interaction.deferPublicMessageUpdate().edit(it)
                    }
                }
            }
        }
    }

    private suspend fun payToolCost(
        s: ClientSession,
        guildId: String,
        characterId: String,
        cost: AbilityCost,
        totalToolsNumber: Int
    ): Boolean {
        val moCost = costPerToolNumber[totalToolsNumber.coerceAtMost(delaysPerToolNumber.size - 1)]
        val moneyStep = db.charactersScope.subtractMoney(s, guildId, characterId, moCost)
        val itemsStep = cost.itemsCost.all { (material, qty) ->
            db.charactersScope.removeItemFromInventory(s, guildId, characterId, material, qty)
        }
        val itemsRecord = cost.itemsCost.mapValues { it.value.toFloat() } + (transactionMoney to moCost)
        val transactionsStep = db.characterTransactionsScope.addTransactionForCharacter(
            s, guildId, characterId, Transaction(
                Date(), null, "BUY TOOL", TransactionType.REMOVE, itemsRecord))
        return moneyStep && itemsStep && transactionsStep
    }

    private suspend fun assignToolToCharacterImmediately(guildId: String, tool: ToolProficiency, character: String, toolCount: Int, locale: String) =
        db.transaction(guildId) { s ->
            val cost = tool.cost ?: throw IllegalStateException("This tool cannot be bought")
            val payStep = payToolCost(s, guildId, character, cost, toolCount)
            val proficiencyStep = db.charactersScope.addProficiencyToCharacter(s, guildId, character, ProficiencyStub(tool.id, tool.name))

            payStep && proficiencyStep && db.characterTransactionsScope.addTransactionForCharacter(
                s, guildId, character, Transaction(Date(), null, "BUY TOOL", TransactionType.ADD, mapOf(tool.name to 1f))
            )
        }.let {
            when {
                it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
            }
        }

    private suspend fun payAndDelayAssignment(guildId: String, tool: ToolProficiency, character: String, toolCount: Int, locale: String) =
        db.transaction(guildId) { s ->
            val cost = tool.cost ?: throw IllegalStateException("This tool cannot be bought")
            val payStep = payToolCost(s, guildId, character, cost, toolCount)

            val delay = Date(
                System.currentTimeMillis()
                    + (cost.timeRequired ?: 0)
                    + delaysPerToolNumber[toolCount.coerceAtMost(delaysPerToolNumber.size - 1)].inWholeMilliseconds
            )

            val task = ScheduledEvent(
                uuid(),
                ScheduledEventType.GIVE_TOOL,
                Date(),
                delay,
                ScheduledEventState.SCHEDULED,
                mapOf(
                    ScheduledEventArg.PROFICIENCY_ID to tool.id,
                    ScheduledEventArg.TARGET to character
                )
            )

            cacheManager.scheduleEvent(Snowflake(guildId), task)

            payStep
        }.let {
            when {
                it.committed -> createGenericEmbedSuccess(
                "${ToolBuyLocale.READY_ON.locale(locale)}: ${dateFormatter.format(Date(
                    System.currentTimeMillis()
                            + (tool.cost?.timeRequired ?: 0)
                            + delaysPerToolNumber[toolCount.coerceAtMost(delaysPerToolNumber.size - 1)].inWholeMilliseconds
                ))}"
            )
                it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
            }
        }

    private fun Character.missingMaterials(tool: ToolProficiency) =
        tool.cost?.itemsCost?.mapValues {
            it.value - inventory.getOrDefault(it.key, 0)
        }?.filterValues {
            it > 0
        } ?: emptyMap()

    private suspend fun checkRequirementsAndBuyTool(guildId: String, tool: ToolProficiency, character: Character, locale: String): InteractionResponseModifyBuilder.() -> Unit {
        val tasks = db.scheduledEventsScope.getAllScheduledEvents(guildId, ScheduledEventState.SCHEDULED)
        val futureTools = tasks.filter { task ->
            task.type == ScheduledEventType.GIVE_TOOL && task.args.any {
                it.key == ScheduledEventArg.TARGET && it.value == character.id
            }
        }.mapNotNull { task ->
            task.args[ScheduledEventArg.PROFICIENCY_ID]
        }.map { ProficiencyStub(it, it) }.toSet()
        val totalToolsNumber = futureTools.size + character.proficiencies.size
        return when {
            character.proficiencies.any { it.id == tool.id } -> createGenericEmbedError(ToolBuyLocale.ALREADY_POSSESS.locale(locale))
            futureTools.any { it.id == tool.id } -> createGenericEmbedError(ToolBuyLocale.ALREADY_POSSESS.locale(locale))
            tool.cost == null -> createGenericEmbedError(ToolBuyLocale.CANNOT_BUY.locale(locale))
            character.money < costPerToolNumber[totalToolsNumber.coerceAtMost(delaysPerToolNumber.size - 1)] -> createGenericEmbedError(CommonLocale.NOT_ENOUGH_MONEY.locale(locale))
            character.missingMaterials(tool).isNotEmpty() -> createGenericEmbedError(
                ToolBuyLocale.MISSING_MATERIALS.locale(locale) + ": " + character.missingMaterials(tool).entries.joinToString(", ") {
                    "${it.key} x${it.value}"
                }
            )
            tool.cost?.timeRequired == null && totalToolsNumber == 0 -> assignToolToCharacterImmediately(guildId, tool, character.id, totalToolsNumber, locale)
            else -> payAndDelayAssignment(guildId, tool, character.id, totalToolsNumber, locale)
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        val tools = cacheManager.getCollectionOfType<ToolProficiency>(guildId)
        val choice = event.interaction.command.strings["proficiency"] ?: throw IllegalStateException("Proficiency not found")
        withOneActiveCharacterOrErrorMessage(responsible, this) { character ->
            tools.firstOrNull { it.name == choice }?.let {
                checkRequirementsAndBuyTool(guildId.toString(), it, character, locale)
            } ?: tools.maxByOrNull { choice.levenshteinDistance(it.name) }?.let { probableItem ->
                val interactionId = compactUuid()
                interactionCache.put(
                    interactionId,
                    AssignToolInteractionData(responsible.id, character, probableItem)
                )
                alternativeOptionMessage(locale, choice, probableItem.name, buildElementId(interactionId))
            } ?:  createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))
        }
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        response.respond(builder)
    }

}