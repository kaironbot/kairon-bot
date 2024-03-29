package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.common.entity.ChannelType
import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.channel
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotCommand
import org.wagham.commands.SimpleResponseSlashCommand
import org.wagham.components.CacheManager
import org.wagham.config.Channels
import org.wagham.config.locale.commands.SetChannelLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.createGenericEmbedSuccess
import org.wagham.utils.defaultLocale

@BotCommand("all")
class SetChannelCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SimpleResponseSlashCommand() {

    override val commandName = "set_channel"
    override val defaultDescription = SetChannelLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = SetChannelLocale.DESCRIPTION.localeMap

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            defaultDescription
        ) {
            localeDescriptions.forEach{ (locale, description) ->
                description(locale, description)
            }
            string("channel_type", SetChannelLocale.CHANNEL_TYPE.locale("en")) {
                SetChannelLocale.CHANNEL_TYPE.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = true
                Channels.values().forEach {
                    choice(it.description, it.name)
                }
            }
            channel("set_channel", SetChannelLocale.CHANNEL.locale("en")) {
                SetChannelLocale.CHANNEL.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = true
                autocomplete = true
                channelTypes = listOf(ChannelType.GuildText)
            }
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value ?: throw GuildNotFoundException()
        val serverConfig = cacheManager.getConfig(guildId, true)
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        cacheManager.setConfig(
            guildId,
            serverConfig.copy(
               channels = serverConfig.channels +
                       (event.interaction.command.strings["channel_type"]!!
                               to event.interaction.command.channels["set_channel"]!!.id.toString())
            )
        )
        return createGenericEmbedSuccess(
            "${SetChannelLocale.CURRENT_CHANNEL.locale(locale).replace("CHANNEL_TYPE", event.interaction.command.strings["channel_type"]!!)}  <#${event.interaction.command.channels["set_channel"]!!.id}>"
        )
    }

}