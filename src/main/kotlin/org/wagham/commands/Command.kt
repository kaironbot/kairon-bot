package org.wagham.commands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.entity.interaction.response.PublicMessageInteractionResponse
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.RequestBuilder
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.components.CacheManager
import org.wagham.db.KabotMultiDBClient
import org.wagham.entities.InteractionParameters
import org.wagham.exceptions.GuildNotFoundException

interface Command<T: RequestBuilder<*>> {
    val kord: Kord
    val db: KabotMultiDBClient
    val cacheManager: CacheManager
    val commandName: String
    val defaultDescription: String
    val localeDescriptions: Map<Locale, String>

    suspend fun registerCommand()
    fun registerCallback()
    suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): T.() -> Unit
    suspend fun handleResponse(
        builder: T.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent)

    fun extractCommonParameters(event: GuildChatInputCommandInteractionCreateEvent): InteractionParameters {
        val guildId = event.interaction.data.guildId.value ?: throw GuildNotFoundException()
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        return InteractionParameters(guildId, locale)
    }
}