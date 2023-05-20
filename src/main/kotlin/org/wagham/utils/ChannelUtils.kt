package org.wagham.utils

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.entity.channel.MessageChannel
import org.wagham.components.CacheManager
import org.wagham.config.Channels
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