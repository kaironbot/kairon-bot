package org.wagham.events

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.event.message.MessageCreateEvent

abstract class RestrictedGuildEvent : Event {

    protected suspend fun isAllowed(guildId: Snowflake?, message: MessageBehavior) =
        guildId != null && cacheManager.getConfig(guildId).let {
            it.eventChannels[eventId] == null || (
                it.eventChannels[eventId] != null && it.eventChannels[eventId]!!.let { channels ->
                    channels.isEmpty() || channels.contains(message.channelId.toString())
                }
            )
        }

}