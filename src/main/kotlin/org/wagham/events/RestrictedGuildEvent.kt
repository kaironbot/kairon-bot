package org.wagham.events

import dev.kord.core.event.message.MessageCreateEvent

abstract class RestrictedGuildEvent : Event {

    protected suspend fun isAllowed(event: MessageCreateEvent) =
        event.guildId != null && cacheManager.getConfig(event.guildId!!).let {
            it.eventChannels[eventId] == null || (
                it.eventChannels[eventId] != null && it.eventChannels[eventId]!!.let { channels ->
                    channels.isEmpty() || channels.contains(event.message.channelId.toString())
                }
            )
        }

}