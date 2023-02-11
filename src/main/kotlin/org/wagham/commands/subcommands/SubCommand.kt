package org.wagham.commands.subcommands

import dev.kord.core.Kord
import dev.kord.core.entity.interaction.response.PublicMessageInteractionResponse
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.components.CacheManager
import org.wagham.db.KabotMultiDBClient

interface SubCommand {
    val kord: Kord
    val db: KabotMultiDBClient
    val cacheManager: CacheManager
    val subcommandName: String
    val subcommandDescription: Map<String, String>

    fun create(ctx: RootInputChatBuilder)
    suspend fun init()
    suspend fun handle(command: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit
    fun handleResponse(msg: PublicMessageInteractionResponse, event: GuildChatInputCommandInteractionCreateEvent)
}