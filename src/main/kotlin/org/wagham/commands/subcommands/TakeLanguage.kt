package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.TakeCommand
import org.wagham.components.CacheManager
import org.wagham.components.MultiCharacterCommand
import org.wagham.components.MultiCharacterManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.TakeItemLocale
import org.wagham.config.locale.subcommands.TakeLanguageLocale
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

@BotSubcommand("all", TakeCommand::class)
class TakeLanguage(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder>, MultiCharacterCommand<LanguageProficiency> {

    override val commandName = "language"
    override val defaultDescription = TakeItemLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = TakeItemLocale.DESCRIPTION.localeMap
    private val multiCharacterManager = MultiCharacterManager(db, kord, this)

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("language", TakeLanguageLocale.LANGUAGE.locale("en")) {
            TakeLanguageLocale.LANGUAGE.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
        user("target", TakeLanguageLocale.TARGET.locale("en")) {
            TakeLanguageLocale.TARGET.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
            autocomplete = true
        }
    }

    override suspend fun registerCommand() {}

    override suspend fun multiCharacterAction(
        interaction: ComponentInteraction,
        characters: List<Character>,
        context: LanguageProficiency,
        sourceCharacter: Character?
    ) {
        interaction.deferPublicMessageUpdate().edit(
            executeTransaction(characters.first(), context, interaction.extractCommonParameters())
        )
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        val target = event.interaction.command.users["target"] ?: throw IllegalStateException("Target not found")
        val language = event.interaction.command.strings["language"]
            ?.let { l ->
                cacheManager.getCollectionOfType<LanguageProficiency>(guildId).firstOrNull {
                    it.name == l
                }
            } ?: throw IllegalStateException(TakeLanguageLocale.NOT_FOUND.locale(locale))
        val targetOrSelectionContext = multiCharacterManager.startSelectionOrReturnCharacters(listOf(target), null, language, this)
        when {
            targetOrSelectionContext.characters != null -> executeTransaction(targetOrSelectionContext.characters.first(), language, this)
            targetOrSelectionContext.response != null -> targetOrSelectionContext.response
            else -> createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))
        }
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        response.respond(builder)
    }

    private suspend fun executeTransaction(character: Character, language: LanguageProficiency, params: InteractionParameters) = with(params) {
        db.transaction(guildId.toString()) { session ->
            db.charactersScope.removeLanguageFromCharacter(session, guildId.toString(), character.id, ProficiencyStub(language.id, language.name))
            db.characterTransactionsScope.addTransactionForCharacter(
                session,
                guildId.toString(),
                character.id,
                Transaction(Date(), null, "TAKE", TransactionType.REMOVE, mapOf(language.name to 1f))
            )
        }.let {
            when {
                it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
            }
        }
    }
}