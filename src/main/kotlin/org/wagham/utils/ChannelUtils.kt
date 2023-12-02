package org.wagham.utils

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.entity.channel.MessageChannel
import org.wagham.components.CacheManager
import org.wagham.config.Channels
import org.wagham.events.Event
import org.wagham.exceptions.ChannelNotFoundException

suspend fun Kord.getChannelOfTypeOrDefault(guildId: Snowflake, channelType: Channels, cacheManager: CacheManager) =
    cacheManager.getConfig(guildId).channels[channelType.name]
        ?.let { Snowflake(it) }
        ?.let { defaultSupplier.getChannel(it).asChannelOf<MessageChannel>() }
        ?: defaultSupplier.getGuild(guildId).getSystemChannel()
        ?: throw Exception("$channelType channel not found")

suspend fun Kord.getChannelOfType(guildId: Snowflake, channelType: Channels, cacheManager: CacheManager) =
    (cacheManager.getConfig(guildId).channels[channelType.name]
        ?: throw ChannelNotFoundException(channelType.name) )
        .let { Snowflake(it) }
        .let {
            defaultSupplier.getChannel(it).asChannelOf<MessageChannel>()
        }

/**
 * Returns the [Channels] for the specified guild id.
 *
 * @receiver an [Event].
 * @param guildId the id of the guild where to get the channel.
 * @param channelType a [Channels] that defines the channel type.
 * @return a [MessageChannel]
 * @throws [ChannelNotFoundException] if no channel with the specified [channelType] was defined in the guild.
 */
suspend fun Event.getChannelOfType(guildId: Snowflake, channelType: Channels) =
    (cacheManager.getConfig(guildId).channels[channelType.name]
        ?: throw ChannelNotFoundException(channelType.name) )
        .let { Snowflake(it) }
        .let {
            kord.defaultSupplier.getChannel(it).asChannelOf<MessageChannel>()
        }