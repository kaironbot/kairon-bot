package org.wagham.commands.subcommands

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.User
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.AssignCommand
import org.wagham.components.CacheManager
import org.wagham.components.MultiCharacterCommand
import org.wagham.components.MultiCharacterManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.AssignLanguageLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.TransactionType
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Character
import org.wagham.db.models.LanguageProficiency
import org.wagham.db.models.embed.ProficiencyStub
import org.wagham.db.models.embed.Transaction
import org.wagham.entities.InteractionParameters
import org.wagham.utils.*
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.TimeUnit

@BotSubcommand("all", AssignCommand::class)
class AssignLanguage(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder>, MultiCharacterCommand<LanguageProficiency> {

    companion object {
        private data class AssignLanguageInteractionData(
            val responsible: Snowflake,
            val language: LanguageProficiency,
            val targetUser: User
        )
    }

    override val commandName = "language"
    override val defaultDescription = AssignLanguageLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = AssignLanguageLocale.DESCRIPTION.localeMap
    private val multiCharacterManager = MultiCharacterManager(db, kord, this)
    private val interactionCache: Cache<String, AssignLanguageInteractionData> =
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
            if (verifyId(interaction.componentId)) {
                val (id) = extractComponentsFromComponentId(interaction.componentId)
                val params = interaction.extractCommonParameters()
                val data = interactionCache.getIfPresent(id)
                when {
                    data == null -> interaction.respondWithExpirationError(params.locale)
                    data.responsible != params.responsible.id -> interaction.respondWithForbiddenError(params.locale)
                    else -> {
                        try {
                            val targetsOrSelectionContext = multiCharacterManager.startSelectionOrReturnCharacters(
                                listOf(data.targetUser),
                                null,
                                data.language,
                                params
                            )
                            when {
                                targetsOrSelectionContext.characters != null -> assignLanguageToCharacter(data.language, targetsOrSelectionContext.characters.first().id, params)
                                targetsOrSelectionContext.response != null -> targetsOrSelectionContext.response
                                else -> createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(params.locale))
                            }
                        } catch (e: Exception) {
                            when(e) {
                                is NoActiveCharacterException -> createGenericEmbedError("<@!${e.playerId}> ${CommonLocale.NO_ACTIVE_CHARACTER.locale(params.locale)}")
                                else -> createGenericEmbedError(e.message ?: e.toString())
                            }
                        }.let {
                            interaction.deferPublicMessageUpdate().edit(it)
                        }
                    }
                }
            }
        }
    }

    private suspend fun assignLanguageToCharacter(language: LanguageProficiency, target: String, params: InteractionParameters) =
        db.transaction(params.guildId.toString()) { s ->
            db.charactersScope.addLanguageToCharacter(
                s,
                params.guildId.toString(),
                target,
                ProficiencyStub(language.id, language.name)
            ) && db.characterTransactionsScope.addTransactionForCharacter(
                s, params.guildId.toString(), target, Transaction(
                    Date(), null, "ASSIGN", TransactionType.ADD, mapOf(language.name to 1f)
                )
            )
        }.let {
            when {
                it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(params.locale))
                it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(params.locale))
                else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
            }
        }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        val languages = cacheManager.getCollectionOfType<LanguageProficiency>(guildId)
        val target = event.interaction.command.users["target"] ?: throw IllegalStateException("Target not found")
        val language = event.interaction.command.strings["language"] ?: throw IllegalStateException("Language not found")
        return languages.firstOrNull { it.name == language }?.let {
            val targetsOrSelectionContext = multiCharacterManager.startSelectionOrReturnCharacters(listOf(target), null, it, this)
            when {
                targetsOrSelectionContext.characters != null -> assignLanguageToCharacter(it, targetsOrSelectionContext.characters.first().id, this)
                targetsOrSelectionContext.response != null -> targetsOrSelectionContext.response
                else -> createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))
            }
        } ?: languages.maxByOrNull { language.levenshteinDistance(it.name) }?.let { probableLanguage ->
            val interactionId = compactUuid()
            interactionCache.put(
                interactionId,
                AssignLanguageInteractionData(responsible.id, probableLanguage, target)
            )
            alternativeOptionMessage(locale, language, probableLanguage.name, buildElementId(interactionId))
        } ?: createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))

    }

    override suspend fun multiCharacterAction(
        interaction: ComponentInteraction,
        characters: List<Character>,
        context: LanguageProficiency,
        sourceCharacter: Character?
    ) {
        val params = interaction.extractCommonParameters()
        interaction.deferPublicMessageUpdate().edit(
            assignLanguageToCharacter(context, characters.first().id, params)
        )
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
       response.respond(builder)
    }
}