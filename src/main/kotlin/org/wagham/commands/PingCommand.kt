package org.wagham.commands

import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.role
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import org.wagham.annotations.BotCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.db.KabotMultiDBClient

@BotCommand
class PingCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SlashCommand() {

    override val commandName = "ping"

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            "Check if the bot is online"
        )
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        return fun InteractionResponseModifyBuilder.() {
            embed {
                color = Colors.DEFAULT.value
                title = "WaghamBot is online"
            }
        }
    }

}