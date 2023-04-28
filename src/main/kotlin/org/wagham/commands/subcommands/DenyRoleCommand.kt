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
import org.wagham.config.locale.subcommands.DenyRoleCommandLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.createGenericEmbedSuccess
import java.lang.IllegalStateException

@BotSubcommand("all", ConfigCmdCommand::class)
class DenyRoleCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "deny_role"
    override val defaultDescription = "Remove a role to the ones that can use this command"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Remove a role to the ones that can use this command",
        Locale.ITALIAN to "Rimuove un ruolo dalla lista di quelli che possono usare questo comando"
    )

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("command", DenyRoleCommandLocale.COMMAND.locale("en")) {
            DenyRoleCommandLocale.COMMAND.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            cacheManager.getCommands().forEach {
                choice(it, it)
            }
            required = true
            autocomplete = true
        }
        role("role", DenyRoleCommandLocale.ROLE.locale("en")) {
            DenyRoleCommandLocale.ROLE.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
            autocomplete = true
        }
    }

    override suspend fun registerCommand() { }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value ?: throw GuildNotFoundException()
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val command = event.interaction.command.strings["command"] ?: throw IllegalStateException("Command not found")
        val role = event.interaction.command.roles["role"] ?: throw IllegalStateException("Role not found")
        val config = cacheManager.getConfig(guildId)
        cacheManager.setConfig(
            guildId,
            config.copy(
                commandsPermissions = config.commandsPermissions +
                        (command to (config.commandsPermissions[command]?.minus(role.id.toString()) ?: emptyList()))
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