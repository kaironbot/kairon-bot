package org.wagham.events

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.event.message.MessageCreateEvent

/**
 * This abstract class should be implemented by all the Events that can run on a subset of the channel.
 * The allowed channels are specified in the Config.
 */
abstract class RestrictedGuildEvent : Event {

    protected suspend fun isAllowed(guildId: Snowflake?, message: MessageBehavior) =
        guildId != null && cacheManager.getConfig(guildId).let {
            it.eventChannels[eventId] == null || (
                it.eventChannels[eventId] != null && it.eventChannels[eventId]!!.allowedChannels.let { channels ->
                    channels.isEmpty() || channels.contains(message.channelId.toString())
                }
            )
        }

    protected suspend fun isEnabled(guildId: Snowflake?) =
        guildId?.let {cacheManager.getConfig(it).eventChannels[eventId]?.enabled } == true

}