package org.wagham.commands

import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.interaction.SubCommand
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.channel
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import org.wagham.annotations.BotCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.db.KabotMultiDBClient

@BotCommand("all")
class ConfigEventCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SlashCommand() {

    override val commandName = "config_event"

    private suspend fun buildAllowedChannelsList(guildId: Snowflake, event: String) =
        cacheManager.getConfig(guildId).eventChannels[event]?.ifEmpty { listOf("All channels") }?.let { channels ->
            channels.joinToString(separator = "") { "$it\n" }
        } ?: "All channels"

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            "Configures the channels for the events"
        ) {

            subCommand("info", "Gets the channel info of an event") {
                string("event", "The event to configure") {
                    required = true
                    cacheManager.getEvents().forEach {
                        choice(it, it)
                    }
                }
            }

            subCommand("add_channel", "Allows the event to be fired on a channel") {
                string("event", "The event to configure") {
                    required = true
                    cacheManager.getEvents().forEach {
                        choice(it, it)
                    }
                }

                channel("channel", "The channel to add") {
                    required = true
                    autocomplete = true
                    channelTypes = listOf(ChannelType.GuildText)
                }
            }

            subCommand("remove_channel", "Allows the event to be fired on a channel") {
                string("event", "The event to configure") {
                    required = true
                    cacheManager.getEvents().forEach {
                        choice(it, it)
                    }
                }

                channel("channel", "The channel to remove") {
                    required = true
                    autocomplete = true
                    channelTypes = listOf(ChannelType.GuildText)
                }
            }
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val command = event.interaction.command as SubCommand
        return when (command.name) {
            "info" -> {
                val channelList = buildAllowedChannelsList(event.interaction.guildId, event.interaction.command.strings["event"]!!)
                fun InteractionResponseModifyBuilder.() {
                    embed {
                        color = Colors.DEFAULT.value
                        title = event.interaction.command.strings["event"]!!
                        description = "**Active in the following channels:**\n$channelList"
                    }
                }
            }
            "add_channel" -> {
                val config = cacheManager.getConfig(event.interaction.guildId, true)
                val currentChannels = config.eventChannels[event.interaction.command.strings["event"]!!] ?: emptyList()
                cacheManager.setConfig(
                    event.interaction.guildId,
                    config.copy(
                        eventChannels = config.eventChannels +
                                (event.interaction.command.strings["event"]!! to
                                        (currentChannels + event.interaction.command.channels["channel"]!!.id.toString()))
                    )
                )
                fun InteractionResponseModifyBuilder.() {
                    embed {
                        color = Colors.DEFAULT.value
                        title = "Operation completed successfully"
                    }
                }
            }
            else -> {
                val config = cacheManager.getConfig(event.interaction.guildId, true)
                val currentChannels = config.eventChannels[event.interaction.command.strings["event"]!!] ?: emptyList()
                cacheManager.setConfig(
                    event.interaction.guildId,
                    config.copy(
                        eventChannels = config.eventChannels +
                                (event.interaction.command.strings["event"]!! to
                                        (currentChannels - event.interaction.command.channels["channel"]!!.id.toString()))
                    )
                )
                fun InteractionResponseModifyBuilder.() {
                    embed {
                        color = Colors.DEFAULT.value
                        title = "Operation completed successfully"
                    }
                }
            }
        }

    }

}