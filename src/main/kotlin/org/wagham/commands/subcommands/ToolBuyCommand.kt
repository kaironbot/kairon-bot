package org.wagham.commands.subcommands

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
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
import kotlinx.coroutines.flow.first
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.ToolCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.ToolBuyLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.TransactionType
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Character
import org.wagham.db.models.ToolProficiency
import org.wagham.db.models.embed.ProficiencyStub
import org.wagham.db.models.embed.Transaction
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.*
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.TimeUnit

@BotSubcommand("all", ToolCommand::class)
class ToolBuyCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "buy"
    override val defaultDescription = "Buy a tool proficiency with the current character"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Buy a tool proficiency with the current character",
        Locale.ITALIAN to "Compra la competenza in uno strumento con il personaggio corrente"
    )
    private val interactionCache: Cache<Snowflake, Snowflake> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build()

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
            val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
            if(interaction.componentId.startsWith("${this@ToolBuyCommand::class.qualifiedName}") && interactionCache.getIfPresent(interaction.message.id) == interaction.user.id) {
                val guildId = interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
                val target = interactionCache.getIfPresent(interaction.message.id) ?: throw IllegalStateException("Cannot find targets")
                val choice = Regex("${this@ToolBuyCommand::class.qualifiedName}-(.+)")
                    .find(interaction.componentId)
                    ?.groupValues
                    ?.get(1) ?: throw IllegalStateException("Cannot parse parameters")
                val tools = cacheManager.getCollectionOfType<ToolProficiency>(guildId)
                checkRequirementsAndBuyTool(guildId, tools.first { it.name == choice }, target, locale).let {
                    interaction.deferPublicMessageUpdate().edit(it)
                }
            } else if (interaction.componentId.startsWith("${this@ToolBuyCommand::class.qualifiedName}") && interactionCache.getIfPresent(interaction.message.id) == null) {
                interaction.deferEphemeralMessageUpdate().edit(createGenericEmbedError(CommonLocale.INTERACTION_EXPIRED.locale(locale)))
            } else if (interaction.componentId.startsWith("${this@ToolBuyCommand::class.qualifiedName}")) {
                interaction.deferEphemeralResponse().respond(createGenericEmbedError(CommonLocale.INTERACTION_STARTED_BY_OTHER.locale(locale)))
            }
        }
    }

    private suspend fun assignToolToCharacter(guildId: String, tool: ToolProficiency, character: String, locale: String) =
        db.transaction(guildId) { s ->
            val moneyStep = db.charactersScope.subtractMoney(s, guildId, character, tool.cost!!.moCost)
            val itemsStep = tool.cost!!.itemsCost.all { (material, qty) ->
                db.charactersScope.removeItemFromInventory(s, guildId, character, material, qty)
            }
            val proficiencyStep = db.charactersScope.addProficiencyToCharacter(s, guildId, character, ProficiencyStub(tool.id, tool.name))

            val itemsRecord = tool.cost!!.itemsCost.mapValues { it.value.toFloat() } +
                    (transactionMoney to tool.cost!!.moCost)

            val transactionsStep = db.characterTransactionsScope.addTransactionForCharacter(
                    s, guildId, character, Transaction(
                        Date(), null, "BUY TOOL", TransactionType.REMOVE, itemsRecord)) &&
                db.characterTransactionsScope.addTransactionForCharacter(
                    s, guildId, character, Transaction(Date(), null, "BUY TOOL", TransactionType.ADD, mapOf(tool.name to 1f))
                )
            moneyStep && itemsStep && proficiencyStep && transactionsStep
        }.let {
            when {
                it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
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

    private suspend fun checkRequirementsAndBuyTool(guildId: String, tool: ToolProficiency, player: Snowflake, locale: String): InteractionResponseModifyBuilder.() -> Unit {
        //TODO fix this
        val character = db.charactersScope.getActiveCharacters(guildId, player.toString()).first()
        return when {
            character.proficiencies.any { it.id == tool.id } -> createGenericEmbedError(ToolBuyLocale.ALREADY_POSSESS.locale(locale))
            tool.cost == null -> createGenericEmbedError(ToolBuyLocale.CANNOT_BUY.locale(locale))
            character.money < tool.cost!!.moCost -> createGenericEmbedError(CommonLocale.NOT_ENOUGH_MONEY.locale(locale))
            character.missingMaterials(tool).isNotEmpty() -> createGenericEmbedError(
                ToolBuyLocale.MISSING_MATERIALS.locale(locale) + ": " + character.missingMaterials(tool).entries.joinToString(", ") {
                    "${it.key} x${it.value}"
                }
            )
            else -> assignToolToCharacter(guildId, tool, character.id, locale)
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val params = event.extractCommonParameters()
        val tools = cacheManager.getCollectionOfType<ToolProficiency>(params.guildId)
        val choice = event.interaction.command.strings["proficiency"] ?: throw IllegalStateException("Proficiency not found")
        val target = event.interaction.user.id
        return try {
            if (tools.firstOrNull { it.name == choice } == null) {
                val probableItem = tools.maxByOrNull { choice.levenshteinDistance(it.name) }
                alternativeOptionMessage(params.locale, choice, probableItem?.name ?: "", buildElementId(probableItem?.name ?: ""))
            } else {
                checkRequirementsAndBuyTool(params.guildId.toString(), tools.first { it.name == choice }, target, params.locale)
            }
        } catch (e: NoActiveCharacterException) {
            createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(params.locale))
        }
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        val msg = response.respond(builder)
        interactionCache.put(
            msg.message.id,
            event.interaction.user.id
        )
    }

}