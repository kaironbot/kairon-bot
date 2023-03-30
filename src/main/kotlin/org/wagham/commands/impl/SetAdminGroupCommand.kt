package org.wagham.commands.impl

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.role
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import org.wagham.annotations.BotCommand
import org.wagham.commands.SlashCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.db.KabotMultiDBClient
import org.wagham.exceptions.*

@BotCommand("all")
class SetAdminGroupCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SlashCommand() {

    override val commandName = "set_admin_role"
    override val commandDescription = "Use this command to configure the admin role for this server"

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            commandDescription
        ) {
            role("role", "The admin role") {
                autocomplete = true
                required = true
            }
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value ?: throw GuildNotFoundException()
        val serverConfig = cacheManager.getConfig(guildId, true)
        if(!isUserAuthorized(guildId, event.interaction, serverConfig.adminRoleId?.let { listOf(Snowflake(it)) } ?: emptyList()))
            throw UnauthorizedException()
        return event.interaction.command.roles["role"]?.let {
            cacheManager.setConfig(
                guildId,
                serverConfig.copy(adminRoleId = it.id.toString())
            )
            fun InteractionResponseModifyBuilder.() {
                embed {
                    title = "Operation executed successfully"
                    description = "Current admin role is: <@&${it.id}>"
                    color = Colors.DEFAULT.value
                }
            }
        } ?: throw InvalidCommandArgumentException()
    }

}