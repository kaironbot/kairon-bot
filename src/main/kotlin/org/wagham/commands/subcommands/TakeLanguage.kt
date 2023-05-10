package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
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
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.TakeLanguageLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.LanguageProficiency
import org.wagham.db.models.embed.ProficiencyStub
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.createGenericEmbedError
import org.wagham.utils.createGenericEmbedSuccess
import java.lang.IllegalStateException

@BotSubcommand("all", TakeCommand::class)
class TakeLanguage(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "language"
    override val defaultDescription = "Take a language from a player"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Assign a language from a player",
        Locale.ITALIAN to "Togli un linguaggio a un giocatore"
    )

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

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val target = event.interaction.command.users["target"]?.id ?: throw IllegalStateException("Target not found")
        val language = event.interaction.command.strings["language"]
            ?.let { l ->
                cacheManager.getCollectionOfType<LanguageProficiency>(guildId).firstOrNull {
                    it.name == l
                }
            } ?: throw IllegalStateException(TakeLanguageLocale.NOT_FOUND.locale(locale))
        return try {
            val character = db.charactersScope.getActiveCharacter(guildId, target.toString())
            db.transaction(guildId) { s ->
                db.charactersScope.removeLanguageFromCharacter(
                    s,
                    guildId,
                    character.id,
                    ProficiencyStub(language.id, language.name)
                )
            }.let {
                when {
                    it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                    it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                    else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
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
        response.respond(builder)
    }
}