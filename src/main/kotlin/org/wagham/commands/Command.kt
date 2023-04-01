package org.wagham.commands

import dev.kord.core.Kord
import dev.kord.core.entity.interaction.response.PublicMessageInteractionResponse
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.RequestBuilder
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.components.CacheManager
import org.wagham.db.KabotMultiDBClient

interface Command<T: RequestBuilder<*>> {
    val kord: Kord
    val db: KabotMultiDBClient
    val cacheManager: CacheManager
    val commandName: String
    val commandDescription: String

    suspend fun registerCommand()
    fun registerCallback()
    suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): T.() -> Unit
    suspend fun handleResponse(
        builder: T.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent)
}