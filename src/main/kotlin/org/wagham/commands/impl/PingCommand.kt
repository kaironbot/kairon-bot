package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.embed
import org.wagham.annotations.BotCommand
import org.wagham.commands.SimpleResponseSlashCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.commands.PingLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.utils.defaultLocale

@BotCommand("all")
class PingCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SimpleResponseSlashCommand() {

    override val commandName = "ping"
    override val defaultDescription = PingLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = PingLocale.DESCRIPTION.localeMap

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            defaultDescription
        ) {
            localeDescriptions.forEach{ (locale, description) ->
                description(locale, description)
            }
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        return fun InteractionResponseModifyBuilder.() {
            val commands = cacheManager.getCommands().ifEmpty { listOf(PingLocale.NO_COMMAND_FOUND.locale(locale)) }
            val events = cacheManager.getEvents().ifEmpty { listOf(PingLocale.NO_EVENT_FOUND.locale(locale)) }
            embed {
                color = Colors.DEFAULT.value
                title = "WaghamBot is online"
                description = buildString {
                    append("**${PingLocale.COMMANDS.locale(locale)}**\n")
                    append(commands.joinToString(separator = "") { "\\$it\n" })
                    append("**${PingLocale.EVENTS.locale(locale)}**\n")
                    append(events.joinToString(separator = "") { it+"\n" })
                }
            }
        }
    }

}