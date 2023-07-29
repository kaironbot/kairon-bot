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
import kotlinx.coroutines.flow.first
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.TakeCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.TakeToolLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.ToolProficiency
import org.wagham.db.models.embed.ProficiencyStub
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.createGenericEmbedError
import org.wagham.utils.createGenericEmbedSuccess
import java.lang.IllegalStateException

@BotSubcommand("all", TakeCommand::class)
class TakeTool(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "tool"
    override val defaultDescription = "Take a tool proficiency from a player"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Take a tool proficiency from a player",
        Locale.ITALIAN to "Rimuovi la competenza in uno strumento a un giocatore"
    )

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("tool", TakeToolLocale.TOOL.locale("en")) {
            TakeToolLocale.TOOL.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
        user("target", TakeToolLocale.TARGET.locale("en")) {
            TakeToolLocale.TARGET.localeMap.forEach{ (locale, description) ->
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
        val tool = event.interaction.command.strings["tool"]
            ?.let { l ->
                cacheManager.getCollectionOfType<ToolProficiency>(guildId).firstOrNull {
                    it.name == l
                }
            } ?: throw IllegalStateException(TakeToolLocale.NOT_FOUND.locale(locale))
        return try {
            //TODO fix this
            val character = db.charactersScope.getActiveCharacters(guildId, target.toString()).first()
            db.charactersScope.removeProficiencyFromCharacter(
                guildId,
                character.id,
                ProficiencyStub(tool.id, tool.name)
            ).let {
                when {
                    it -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                    else -> createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))
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