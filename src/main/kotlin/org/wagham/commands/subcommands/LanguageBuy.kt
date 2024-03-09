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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.LanguageCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.LanguageBuyLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.ScheduledEventArg
import org.wagham.db.enums.ScheduledEventState
import org.wagham.db.enums.ScheduledEventType
import org.wagham.db.enums.TransactionType
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Character
import org.wagham.db.models.LanguageProficiency
import org.wagham.db.models.ScheduledEvent
import org.wagham.db.models.embed.AbilityCost
import org.wagham.db.models.embed.ProficiencyStub
import org.wagham.db.models.embed.Transaction
import org.wagham.utils.*
import java.lang.IllegalStateException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@BotSubcommand("all", LanguageCommand::class)
class LanguageBuy(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    companion object {
        private data class AssignLanguageInteractionData(
            val responsible: Snowflake,
            val character: Character,
            val language: LanguageProficiency
        )
    }

    override val commandName = "buy"
    private val dateFormatter = SimpleDateFormat("dd/MM/YYYY HH:mm:ss")
    override val defaultDescription = LanguageBuyLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = LanguageBuyLocale.DESCRIPTION.localeMap
    private val interactionCache: Cache<String, AssignLanguageInteractionData> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build()

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("language", LanguageBuyLocale.PROFICIENCY.locale("en")) {
            LanguageBuyLocale.PROFICIENCY.localeMap.forEach{ (locale, description) ->
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
                    else -> checkRequirementsAndBuyLanguage(params.guildId.toString(), data.language, data.character, params.locale).let {
                        interaction.deferPublicMessageUpdate().edit(it)
                    }
                }
            }
        }
    }

    private suspend fun payLanguageCost(
        s: ClientSession,
        guildId: String,
        characterId: String,
        cost: AbilityCost
    ): Boolean {
        val moneyStep = db.charactersScope.subtractMoney(s, guildId, characterId, cost.moCost)
        val itemsStep = cost.itemsCost.all { (material, qty) ->
            db.charactersScope.removeItemFromInventory(s, guildId, characterId, material, qty)
        }
        val itemsRecord = cost.itemsCost.mapValues { it.value.toFloat() } + (transactionMoney to cost.moCost)
        val transactionsStep = db.characterTransactionsScope.addTransactionForCharacter(
            s, guildId, characterId, Transaction(
                Date(), null, "BUY LANGUAGE", TransactionType.REMOVE, itemsRecord))
        return moneyStep && itemsStep && transactionsStep
    }

    private suspend fun payAndDelayAssignment(guildId: String, language: LanguageProficiency, character: String, locale: String): InteractionResponseModifyBuilder.() -> Unit {
        val cost = language.cost ?: throw IllegalStateException("This tool cannot be bought")
        return db.transaction(guildId) { s ->
            val payStep = payLanguageCost(s, guildId, character, cost)
            mapOf("pay" to payStep)
        }.let {
            when {
                it.committed -> {
                    val delay = Date(System.currentTimeMillis() + (cost.timeRequired ?: 0))

                    val task = ScheduledEvent(
                        uuid(),
                        ScheduledEventType.GIVE_LANGUAGE,
                        Date(),
                        delay,
                        ScheduledEventState.SCHEDULED,
                        mapOf(
                            ScheduledEventArg.PROFICIENCY_ID to language.id,
                            ScheduledEventArg.TARGET to character
                        )
                    )

                    cacheManager.scheduleEvent(Snowflake(guildId), task)
                    createGenericEmbedSuccess(
                        "${LanguageBuyLocale.READY_ON.locale(locale)}: ${dateFormatter.format(Date(
                            System.currentTimeMillis() + (language.cost?.timeRequired ?: 0))
                        )}")
                }
                it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
            }
        }
    }


    private suspend fun assignLanguageToCharacterImmediately(guildId: String, language: LanguageProficiency, character: String, locale: String) =
        db.transaction(guildId) { s ->
            val cost = language.cost ?: throw IllegalStateException("This tool cannot be bought")
            val payStep = payLanguageCost(s, guildId, character, cost)
            val proficiencyStep = db.charactersScope.addLanguageToCharacter(s, guildId, character, ProficiencyStub(language.id, language.name))

            val result = payStep && proficiencyStep && db.characterTransactionsScope.addTransactionForCharacter(
                s, guildId, character, Transaction(Date(), null, "BUY LANGUAGE", TransactionType.ADD, mapOf(language.name to 1f))
            )
            mapOf("result" to result)
        }.let {
            when {
                it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
            }
        }

    private fun Character.missingMaterials(language: LanguageProficiency) =
        language.cost?.itemsCost?.mapValues {
            it.value - inventory.getOrDefault(it.key, 0)
        }?.filterValues {
            it > 0
        } ?: emptyMap()

    private suspend fun checkRequirementsAndBuyLanguage(guildId: String, language: LanguageProficiency, character: Character, locale: String): InteractionResponseModifyBuilder.() -> Unit {
        val tasks = db.scheduledEventsScope.getAllScheduledEvents(guildId, ScheduledEventState.SCHEDULED)
        val learningDate = tasks.filter { task ->
            task.type == ScheduledEventType.GIVE_LANGUAGE && task.args.any {
                it.key == ScheduledEventArg.TARGET && it.value == character.id
            }
        }.firstOrNull { task ->
            task.args[ScheduledEventArg.PROFICIENCY_ID] == language.id
        }?.activation
        return when {
            learningDate != null -> createGenericEmbedError("${LanguageBuyLocale.LEARNING.locale(locale)} ${dateFormatter.format(learningDate)}")
            character.languages.any { it.id == language.id } -> createGenericEmbedError(LanguageBuyLocale.ALREADY_POSSESS.locale(locale))
            language.cost == null -> createGenericEmbedError(LanguageBuyLocale.CANNOT_BUY.locale(locale))
            language.cost?.moCost?.let {
                character.money < it
            } ?: true -> createGenericEmbedError(CommonLocale.NOT_ENOUGH_MONEY.locale(locale))
            character.missingMaterials(language).isNotEmpty() -> createGenericEmbedError(
                LanguageBuyLocale.MISSING_MATERIALS.locale(locale) + ": " + character.missingMaterials(language).entries.joinToString(
                    ", "
                ) { "${it.key} x${it.value}" }
            )
            language.cost?.timeRequired?.let { it > 0 } ?: false -> payAndDelayAssignment(guildId, language, character.id, locale)
            else -> assignLanguageToCharacterImmediately(guildId, language, character.id, locale)
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        val languages = cacheManager.getCollectionOfType<LanguageProficiency>(guildId)
        val choice = event.interaction.command.strings["language"] ?: throw IllegalStateException("Language not found")
        withOneActiveCharacterOrErrorMessage(responsible, this) { character ->
            languages.firstOrNull { it.name == choice }?.let {
                checkRequirementsAndBuyLanguage(guildId.toString(), it, character, locale)
            } ?: languages.maxByOrNull { choice.levenshteinDistance(it.name) }?.let {
                val id = compactUuid()
                interactionCache.put(
                    id,
                    AssignLanguageInteractionData(responsible.id, character, it)
                )
                alternativeOptionMessage(locale, choice, it.name, buildElementId(id))
            } ?: createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))
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