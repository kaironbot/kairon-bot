package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import kotlinx.coroutines.flow.first
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.ConfigCmdCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.subcommands.CommandInfoLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.defaultLocale
import org.wagham.utils.withEventParameters
import java.lang.IllegalStateException

@BotSubcommand("all", ConfigCmdCommand::class)
class CommandInfo(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "info"
    override val defaultDescription = CommandInfoLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = CommandInfoLocale.DESCRIPTION.localeMap

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach { (locale, description) ->
            description(locale, description)
        }
        string("command", CommandInfoLocale.COMMAND.locale("en")) {
            CommandInfoLocale.COMMAND.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            cacheManager.getCommands().forEach {
                choice(it, it)
            }
            required = true
        }
    }

    override suspend fun registerCommand() { }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        val config = cacheManager.getConfig(guildId)
        val command = event.interaction.command.strings["command"] ?: throw IllegalStateException("Command not found")
        val guildCommand = kord.getGlobalApplicationCommands(true).first { it.name == command }
        return fun InteractionResponseModifyBuilder.() {
            embed {
                title = command
                description = guildCommand.data.descriptionLocalizations.value?.get(Locale.fromString(locale)) ?: ""
                field {
                    name = CommandInfoLocale.ALLOWED_ROLES.locale(locale)
                    value = config.commandsPermissions[command]?.takeIf { it.isNotEmpty() }?.let { roles ->
                        roles.joinToString(separator = ", ") { "<@&$it>" }
                    } ?: CommandInfoLocale.EVERYONE_ALLOWED.locale(locale)
                }
                color = Colors.DEFAULT.value
            }
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