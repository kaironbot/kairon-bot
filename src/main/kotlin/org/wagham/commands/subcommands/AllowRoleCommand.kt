package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.ConfigCmdCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.AllowRoleCommandLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.createGenericEmbedSuccess
import org.wagham.utils.defaultLocale
import org.wagham.utils.extractCommonParameters
import java.lang.IllegalStateException

@BotSubcommand("all", ConfigCmdCommand::class)
class AllowRoleCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "allow_role"
    override val defaultDescription = AllowRoleCommandLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = AllowRoleCommandLocale.DESCRIPTION.localeMap

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("command", AllowRoleCommandLocale.COMMAND.locale("en")) {
            AllowRoleCommandLocale.COMMAND.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            cacheManager.getCommands().forEach {
                choice(it, it)
            }
            required = true
        }
        role("role", AllowRoleCommandLocale.ROLE.locale("en")) {
            AllowRoleCommandLocale.ROLE.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
            autocomplete = true
        }
    }

    override suspend fun registerCommand() { }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val params = event.extractCommonParameters()
        val command = event.interaction.command.strings["command"] ?: throw IllegalStateException("Command not found")
        val role = event.interaction.command.roles["role"] ?: throw IllegalStateException("Role not found")
        val config = cacheManager.getConfig(params.guildId)
        cacheManager.setConfig(
            params.guildId,
            config.copy(
                commandsPermissions = config.commandsPermissions +
                    (command to (config.commandsPermissions[command] ?: emptySet()) + role.id.toString())
            )
        )
        return createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(params.locale))
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        response.respond(builder)
    }
}