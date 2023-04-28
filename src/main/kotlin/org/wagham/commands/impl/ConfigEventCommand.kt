package org.wagham.commands.impl

import dev.kord.common.Locale
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
import org.wagham.commands.SimpleResponseSlashCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.commands.ConfigEventLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.utils.createGenericEmbedSuccess

@BotCommand("all")
class ConfigEventCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SimpleResponseSlashCommand() {

    override val commandName = "config_event"
    override val defaultDescription = "Configures the channels for the events"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Configures the channels for the events",
        Locale.ITALIAN to "Configura il canale di attivazione per un evento"
    )

    private suspend fun buildAllowedChannelsList(guildId: Snowflake, event: String) =
        cacheManager.getConfig(guildId).eventChannels[event]?.ifEmpty { listOf("All channels") }?.let { channels ->
            channels.joinToString(separator = "") { "<#$it>\n" }
        } ?: "All channels"

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            defaultDescription
        ) {
            localeDescriptions.forEach{ (locale, description) ->
                description(locale, description)
            }
            subCommand("info", ConfigEventLocale.INFO_SUBCOMMAND_DESCRIPTION.locale("en")) {
                ConfigEventLocale.INFO_SUBCOMMAND_DESCRIPTION.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                string("event", ConfigEventLocale.INFO_SUBCOMMAND_EVENT.locale("en")) {
                    ConfigEventLocale.INFO_SUBCOMMAND_EVENT.localeMap.forEach{ (locale, description) ->
                        description(locale, description)
                    }
                    required = true
                    cacheManager.getEvents().forEach {
                        choice(it, it)
                    }
                }
            }

            subCommand("add_channel", ConfigEventLocale.ADD_SUBCOMMAND_DESCRIPTION.locale("en")) {
                ConfigEventLocale.ADD_SUBCOMMAND_DESCRIPTION.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                string("event", ConfigEventLocale.INFO_SUBCOMMAND_EVENT.locale("en")) {
                    ConfigEventLocale.INFO_SUBCOMMAND_EVENT.localeMap.forEach{ (locale, description) ->
                        description(locale, description)
                    }
                    required = true
                    cacheManager.getEvents().forEach {
                        choice(it, it)
                    }
                }

                channel("channel", ConfigEventLocale.ADD_SUBCOMMAND_CHANNEL.locale("en")) {
                    ConfigEventLocale.ADD_SUBCOMMAND_CHANNEL.localeMap.forEach{ (locale, description) ->
                        description(locale, description)
                    }
                    required = true
                    autocomplete = true
                    channelTypes = listOf(ChannelType.GuildText)
                }
            }

            subCommand("remove_channel", ConfigEventLocale.REMOVE_SUBCOMMAND_DESCRIPTION.locale("en")) {
                ConfigEventLocale.REMOVE_SUBCOMMAND_DESCRIPTION.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                string("event", ConfigEventLocale.INFO_SUBCOMMAND_EVENT.locale("en")) {
                    ConfigEventLocale.INFO_SUBCOMMAND_EVENT.localeMap.forEach{ (locale, description) ->
                        description(locale, description)
                    }
                    required = true
                    cacheManager.getEvents().forEach {
                        choice(it, it)
                    }
                }

                channel("channel", ConfigEventLocale.REMOVE_SUBCOMMAND_CHANNEL.locale("en")) {
                    ConfigEventLocale.REMOVE_SUBCOMMAND_CHANNEL.localeMap.forEach{ (locale, description) ->
                        description(locale, description)
                    }
                    required = true
                    autocomplete = true
                    channelTypes = listOf(ChannelType.GuildText)
                }
            }
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val command = event.interaction.command as SubCommand
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val serverConfig = cacheManager.getConfig(event.interaction.guildId, true)
        return when (command.name) {
            "info" -> {
                val channelList = buildAllowedChannelsList(event.interaction.guildId, event.interaction.command.strings["event"]!!)
                fun InteractionResponseModifyBuilder.() {
                    embed {
                        color = Colors.DEFAULT.value
                        title = event.interaction.command.strings["event"]!!
                        description = "**${ConfigEventLocale.ACTIVE_IN_CHANNELS.locale("en")}**\n$channelList"
                    }
                }
            }
            "add_channel" -> {
                val currentChannels = serverConfig.eventChannels[event.interaction.command.strings["event"]!!] ?: emptyList()
                cacheManager.setConfig(
                    event.interaction.guildId,
                    serverConfig.copy(
                        eventChannels = serverConfig.eventChannels +
                                (event.interaction.command.strings["event"]!! to
                                        (currentChannels + event.interaction.command.channels["channel"]!!.id.toString()))
                    )
                )
                createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
            }
            else -> {
                val currentChannels = serverConfig.eventChannels[event.interaction.command.strings["event"]!!] ?: emptyList()
                cacheManager.setConfig(
                    event.interaction.guildId,
                    serverConfig.copy(
                        eventChannels = serverConfig.eventChannels +
                                (event.interaction.command.strings["event"]!! to
                                        (currentChannels - event.interaction.command.channels["channel"]!!.id.toString()))
                    )
                )
                createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
            }
        }

    }
}