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
import org.wagham.db.models.ServerConfig
import org.wagham.db.models.embed.EventConfig
import org.wagham.utils.createGenericEmbedError
import org.wagham.utils.createGenericEmbedSuccess
import org.wagham.utils.defaultLocale

@BotCommand("all")
class ConfigEventCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SimpleResponseSlashCommand() {

    override val commandName = "event"
    override val defaultDescription = ConfigEventLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = ConfigEventLocale.DESCRIPTION.localeMap

    private suspend fun buildAllowedChannelsList(guildId: Snowflake, event: String) =
        cacheManager.getConfig(guildId).eventChannels[event]?.allowedChannels?.ifEmpty { listOf("All channels") }?.let { channels ->
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

            subCommand("enable", ConfigEventLocale.ENABLE_SUBCOMMAND_DESCRIPTION.locale("en")) {
                ConfigEventLocale.ENABLE_SUBCOMMAND_DESCRIPTION.localeMap.forEach{ (locale, description) ->
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

            subCommand("disable", ConfigEventLocale.DISABLE_SUBCOMMAND_DESCRIPTION.locale("en")) {
                ConfigEventLocale.DISABLE_SUBCOMMAND_DESCRIPTION.localeMap.forEach{ (locale, description) ->
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
        val eventName = event.interaction.command.strings["event"] ?: throw IllegalStateException("Event not found")
        return when (command.name) {
            "info" -> {
                val channelList = buildAllowedChannelsList(event.interaction.guildId, eventName)
                fun InteractionResponseModifyBuilder.() {
                    embed {
                        color = Colors.DEFAULT.value
                        title = event.interaction.command.strings["event"]!!
                        description = buildString {
                            append("**${ConfigEventLocale.ENABLED.locale(locale)}**\n")
                            append(
                                if(serverConfig.eventChannels[eventName]?.enabled == true) CommonLocale.YES.locale(locale)
                                else CommonLocale.NO.locale(locale)
                            )
                            append("\n")
                            append("**${ConfigEventLocale.ACTIVE_IN_CHANNELS.locale("en")}**\n$channelList")
                        }


                    }
                }
            }
            "add_channel" -> {
                val eventConfig = serverConfig.eventChannels[eventName] ?: EventConfig()
                val updatedEventConfig = eventConfig.copy(
                    allowedChannels = eventConfig.allowedChannels + event.interaction.command.channels["channel"]!!.id.toString()
                )
                updateEventConfig(event.interaction.guildId, eventName, serverConfig, updatedEventConfig)
                createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
            }
            "remove_channel" -> {
                val eventConfig = serverConfig.eventChannels[eventName] ?: EventConfig()
                val updatedEventConfig = eventConfig.copy(
                    allowedChannels = eventConfig.allowedChannels - event.interaction.command.channels["channel"]!!.id.toString()
                )
                updateEventConfig(event.interaction.guildId, eventName, serverConfig, updatedEventConfig)
                createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
            }
            "enable" -> {
                val eventConfig = serverConfig.eventChannels[eventName]?.copy(enabled = true) ?: EventConfig(true)
                updateEventConfig(event.interaction.guildId, eventName, serverConfig, eventConfig)
                createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
            }
            "disable" -> {
                val eventConfig = serverConfig.eventChannels[eventName]?.copy(enabled = false) ?: EventConfig(false)
                updateEventConfig(event.interaction.guildId, eventName, serverConfig, eventConfig)
                createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
            }
            else -> createGenericEmbedError(CommonLocale.UNKNOWN_OP.locale(locale))
        }
    }

    private suspend fun updateEventConfig(guildId: Snowflake, eventName: String, serverConfig: ServerConfig, config: EventConfig) =
        cacheManager.setConfig(
            guildId,
            serverConfig.copy(
                eventChannels = serverConfig.eventChannels + (eventName to config)
            )
        )
}