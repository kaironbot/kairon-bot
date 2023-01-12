package org.wagham.events

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.entity.channel.MessageChannel
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import mu.KotlinLogging
import org.wagham.annotations.BotEvent
import org.wagham.components.CacheManager
import org.wagham.db.KabotMultiDBClient
import org.wagham.utils.ActiveUsersReport
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Date
import java.util.Timer
import kotlin.concurrent.schedule

@BotEvent("all")
class CountActiveUsersEvent(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : Event {

    override val eventId = "count_active_users"
    private val logger = KotlinLogging.logger {}

    private suspend fun countActiveUsersForGuild(guildId: Snowflake, yesterday: Instant) =
        cacheManager.getConfig(guildId).eventChannels[eventId]?.fold(Pair(emptySet<Snowflake>(), emptyList<Double>())) { acc, channel ->
            kord.getChannel(Snowflake(channel))
                ?.asChannelOf<MessageChannel>()
                ?.let { messageChannel ->
                    messageChannel.getLastMessage()?.let {
                        messageChannel.getMessagesBefore(it.id)
                    }
                }?.takeWhile {
                    it.timestamp > yesterday
                }?.fold(Pair(emptySet<Snowflake>(), emptyList<Instant>())) { innerAcc, it ->
                    if (it.author != null && !it.author!!.isBot) Pair(innerAcc.first + it.author!!.id, innerAcc.second + it.timestamp)
                    else innerAcc
                }?.let {
                    val average = (0 until it.second.size-1).fold(emptyList<Long>()) { intervals, index ->
                        intervals + (it.second[index] - it.second[index+1]).inWholeSeconds
                    }.average()
                    Pair(
                        acc.first + it.first,
                        acc.second + average
                    )
                } ?: acc
        }?.let {
            ActiveUsersReport(
                it.first.size,
                it.second.average()
            )
        }

    override fun register() {
        val calendar = Calendar.getInstance()
        val startingDate = LocalDateTime.of(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH)+1,
            calendar.get(Calendar.DAY_OF_MONTH),
            23,59, 59
        )
        logger.info { "$eventId task will start on $startingDate" }
        Timer(eventId).schedule(
            Date.from(startingDate.toInstant(ZoneOffset.UTC)),
            24 * 60 * 60 * 1000
        ) {
            runBlocking {
                val yesterday = Instant.fromEpochMilliseconds(LocalDateTime.now().minusDays(1).toInstant(ZoneOffset.UTC).toEpochMilli())
                kord.guilds.collect {
                    countActiveUsersForGuild(it.id, yesterday)?.let { report ->
                        cacheManager.storeUsersReport(it.id, report)
                    }
                }
            }
        }
    }


}