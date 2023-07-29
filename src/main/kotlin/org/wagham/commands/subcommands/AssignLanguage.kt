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
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import kotlinx.coroutines.flow.first
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.AssignCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.AssignLanguageLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.TransactionType
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.LanguageProficiency
import org.wagham.db.models.embed.ProficiencyStub
import org.wagham.db.models.embed.Transaction
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.alternativeOptionMessage
import org.wagham.utils.createGenericEmbedError
import org.wagham.utils.createGenericEmbedSuccess
import org.wagham.utils.levenshteinDistance
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.TimeUnit

@BotSubcommand("all", AssignCommand::class)
class AssignLanguage(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "language"
    override val defaultDescription = "Assign a language to a player"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Assign a language to a player",
        Locale.ITALIAN to "Assegna un linguaggio a un giocatore"
    )
    private val interactionCache: Cache<Snowflake, Pair<Snowflake, Snowflake>> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build()

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("language", AssignLanguageLocale.LANGUAGE.locale("en")) {
            AssignLanguageLocale.LANGUAGE.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
        user("target", AssignLanguageLocale.TARGET.locale("en")) {
            AssignLanguageLocale.TARGET.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
            autocomplete = true
        }
    }

    override suspend fun registerCommand() {
        kord.on<ButtonInteractionCreateEvent> {
            val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
            if(interaction.componentId.startsWith("${this@AssignLanguage::class.qualifiedName}") && interactionCache.getIfPresent(interaction.message.id)?.first == interaction.user.id) {
                val guildId = interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
                val target = interactionCache.getIfPresent(interaction.message.id)?.second ?: throw IllegalStateException("Cannot find targets")
                val language = Regex("${this@AssignLanguage::class.qualifiedName}-([0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12})")
                    .find(interaction.componentId)
                    ?.groupValues
                    ?.get(1)
                    ?: throw IllegalStateException("Cannot parse parameters")
                val languages = cacheManager.getCollectionOfType<LanguageProficiency>(guildId)
                //TODO fix this
                val character = db.charactersScope.getActiveCharacters(guildId, target.toString()).first()
                assignLanguageToCharacter(guildId, languages.first { it.id == language }, character.id).let {
                    when {
                        it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                        it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                        else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
                    }
                }.let {
                    interaction.deferPublicMessageUpdate().edit(it)
                }
            } else if (interaction.componentId.startsWith("${this@AssignLanguage::class.qualifiedName}") && interactionCache.getIfPresent(interaction.message.id) == null) {
                interaction.deferEphemeralMessageUpdate().edit(createGenericEmbedError(CommonLocale.INTERACTION_EXPIRED.locale(locale)))
            } else if (interaction.componentId.startsWith("${this@AssignLanguage::class.qualifiedName}")) {
                interaction.deferEphemeralResponse().respond(createGenericEmbedError(CommonLocale.INTERACTION_STARTED_BY_OTHER.locale(locale)))
            }
        }
    }

    private suspend fun assignLanguageToCharacter(guildId: String, language: LanguageProficiency, target: String) =
        db.transaction(guildId) { s ->
            db.charactersScope.addLanguageToCharacter(
                s,
                guildId,
                target,
                ProficiencyStub(language.id, language.name)
            ) && db.characterTransactionsScope.addTransactionForCharacter(
                s, guildId, target, Transaction(
                    Date(), null, "ASSIGN", TransactionType.ADD, mapOf(language.name to 1f)
                )
            )
        }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val languages = cacheManager.getCollectionOfType<LanguageProficiency>(guildId)
        val target = event.interaction.command.users["target"]?.id ?: throw IllegalStateException("Target not found")
        val language = event.interaction.command.strings["language"] ?: throw IllegalStateException("Language not found")
        return try {
            if (languages.firstOrNull { it.name == language } == null) {
                val probableLanguage = languages.maxByOrNull { language.levenshteinDistance(it.name) }
                alternativeOptionMessage(locale, language, probableLanguage?.name, "${this@AssignLanguage::class.qualifiedName}-${probableLanguage?.id}")
            } else {
                //TODO fix this
                val character = db.charactersScope.getActiveCharacters(guildId, target.toString()).first()
                assignLanguageToCharacter(guildId, languages.first { it.name == language }, character.id).let {
                    when {
                        it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                        it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                        else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
                    }
                }
            }
        } catch (e: NoActiveCharacterException) {
            createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
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
            Pair(
                event.interaction.user.id,
                event.interaction.command.users["target"]!!.id
            )
        )
    }

}