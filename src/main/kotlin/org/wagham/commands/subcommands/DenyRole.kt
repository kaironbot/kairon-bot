package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.role
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.ConfigCmdCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.DenyRoleLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.utils.createGenericEmbedSuccess
import org.wagham.utils.defaultLocale
import org.wagham.utils.withEventParameters
import java.lang.IllegalStateException

@BotSubcommand("all", ConfigCmdCommand::class)
class DenyRole(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "deny_role"
    override val defaultDescription = DenyRoleLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = DenyRoleLocale.DESCRIPTION.localeMap

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("command", DenyRoleLocale.COMMAND.locale("en")) {
            DenyRoleLocale.COMMAND.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            cacheManager.getCommands().forEach {
                choice(it, it)
            }
            required = true
        }
        role("role", DenyRoleLocale.ROLE.locale("en")) {
            DenyRoleLocale.ROLE.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
            autocomplete = true
        }
    }

    override suspend fun registerCommand() { }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        val command = event.interaction.command.strings["command"] ?: throw IllegalStateException("Command not found")
        val role = event.interaction.command.roles["role"] ?: throw IllegalStateException("Role not found")
        val config = cacheManager.getConfig(guildId)
        cacheManager.setConfig(
            guildId,
            config.copy(
                commandsPermissions = config.commandsPermissions +
                        (command to (config.commandsPermissions[command]?.minus(role.id.toString()) ?: emptySet()))
            )
        )
        return createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        response.respond(builder)
    }
}