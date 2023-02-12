package org.wagham.commands

import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.channel
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import org.wagham.annotations.BotCommand
import org.wagham.components.CacheManager
import org.wagham.config.Channels
import org.wagham.config.Colors
import org.wagham.db.KabotMultiDBClient
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.exceptions.UnauthorizedException

@BotCommand("all")
class SetChannelCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SlashCommand() {

    override val commandName = "set_channel"
    override val commandDescription = "Use this command to configure the channels for the bot"

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            commandDescription
        ) {
            string("channel_type", "The channel type") {
                required = true
                Channels.values().forEach {
                    choice(it.description, it.name)
                }
            }
            channel("set_channel", "The channel to set") {
                required = true
                autocomplete = true
                channelTypes = listOf(ChannelType.GuildText)
            }
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value ?: throw GuildNotFoundException()
        val serverConfig = cacheManager.getConfig(guildId, true)
        if(!isUserAuthorized(guildId, event.interaction, serverConfig.adminRoleId?.let { listOf(Snowflake(it)) } ?: emptyList()))
            throw UnauthorizedException()
        cacheManager.setConfig(
            guildId,
            serverConfig.copy(
               channels = serverConfig.channels +
                       (event.interaction.command.strings["channel_type"]!!
                               to event.interaction.command.channels["set_channel"]!!.id.toString())
            )
        )
        return fun InteractionResponseModifyBuilder.() {
            embed {
                title = "Operation executed successfully"
                description = "Current ${event.interaction.command.strings["channel_type"]!!} channel is: <#${event.interaction.command.channels["set_channel"]!!.id}>"
                color = Colors.DEFAULT.value
            }
        }
    }

}