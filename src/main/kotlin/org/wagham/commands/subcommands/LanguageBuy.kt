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
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.LanguageCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.LanguageBuyLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.TransactionType
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Character
import org.wagham.db.models.LanguageProficiency
import org.wagham.db.models.embed.ProficiencyStub
import org.wagham.db.models.embed.Transaction
import org.wagham.utils.*
import java.lang.IllegalStateException
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
                    data == null -> interaction.respondWithExpirationError(params.locale)
                    data.responsible != params.responsible.id -> interaction.respondWithForbiddenError(params.locale)
                    else -> checkRequirementsAndBuyLanguage(params.guildId.toString(), data.language, data.character, params.locale).let {
                        interaction.deferPublicMessageUpdate().edit(it)
                    }
                }
            }
        }
    }

    private suspend fun assignLanguageTransaction(guildId: String, language: LanguageProficiency, character: String, locale: String) =
        db.transaction(guildId) { s ->
            val moneyStep = db.charactersScope.subtractMoney(s, guildId, character, language.cost!!.moCost)
            val itemsStep = language.cost!!.itemsCost.all { (material, qty) ->
                db.charactersScope.removeItemFromInventory(s, guildId, character, material, qty)
            }
            val proficiencyStep = db.charactersScope.addLanguageToCharacter(s, guildId, character, ProficiencyStub(language.id, language.name))

            val itemsRecord = language.cost!!.itemsCost.mapValues { it.value.toFloat() } +
                    (transactionMoney to language.cost!!.moCost)

            val transactionsStep = db.characterTransactionsScope.addTransactionForCharacter(
                s, guildId, character, Transaction(
                    Date(), null, "BUY LANGUAGE", TransactionType.REMOVE, itemsRecord)) &&
                    db.characterTransactionsScope.addTransactionForCharacter(
                        s, guildId, character, Transaction(Date(), null, "BUY LANGUAGE", TransactionType.ADD, mapOf(language.name to 1f))
                    )
            moneyStep && itemsStep && proficiencyStep && transactionsStep
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
        return when {
            character.languages.any { it.id == language.id } -> createGenericEmbedError(
                LanguageBuyLocale.ALREADY_POSSESS.locale(
                    locale
                )
            )
            language.cost == null -> createGenericEmbedError(LanguageBuyLocale.CANNOT_BUY.locale(locale))
            character.money < language.cost!!.moCost -> createGenericEmbedError(
                CommonLocale.NOT_ENOUGH_MONEY.locale(
                    locale
                )
            )
            character.missingMaterials(language).isNotEmpty() -> createGenericEmbedError(
                LanguageBuyLocale.MISSING_MATERIALS.locale(locale) + ": " + character.missingMaterials(language).entries.joinToString(
                    ", "
                ) {
                    "${it.key} x${it.value}"
                }
            )
            else -> assignLanguageTransaction(guildId, language, character.id, locale)
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