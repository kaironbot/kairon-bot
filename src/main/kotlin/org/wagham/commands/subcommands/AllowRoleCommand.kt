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
import java.lang.IllegalStateException

@BotSubcommand("all", ConfigCmdCommand::class)
class AllowRoleCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "allow_role"
    override val defaultDescription = "Limits the access of a command to a role"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Limits the access of a command to a role",
        Locale.ITALIAN to "Limita agli utenti di un ruolo di eseguire questo comando"
    )

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
        val guildId = event.interaction.data.guildId.value ?: throw GuildNotFoundException()
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val command = event.interaction.command.strings["command"] ?: throw IllegalStateException("Command not found")
        val role = event.interaction.command.roles["role"] ?: throw IllegalStateException("Role not found")
        val config = cacheManager.getConfig(guildId)
        cacheManager.setConfig(
            guildId,
            config.copy(
                commandsPermissions = config.commandsPermissions +
                    (command to (config.commandsPermissions[command] ?: emptyList()) + role.id.toString())
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