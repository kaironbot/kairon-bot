package org.wagham.events

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Message
import dev.kord.core.event.message.ReactionAddEvent
import dev.kord.core.on
import kotlinx.datetime.Instant
import org.wagham.annotations.BotEvent
import org.wagham.components.CacheManager
import org.wagham.db.KabotMultiDBClient
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

data class FlameConfig(
    val activationThreshold: Int,
    val saveThreshold: Int,
    val sentences: Set<String>,
    val meanInterval: Duration
)

@BotEvent("all")
class FlameEvent(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : RestrictedGuildEvent() {

    override val eventId = "flame_event"
    private val flame = "🔥"
    private val flameCache: Cache<Snowflake, FlameConfig> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build()
    private val lastReactions: Cache<Snowflake, Instant> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build()
    private val messages = mutableMapOf<Snowflake, List<Message>>()


    private suspend fun getFlameParameters(guildId: Snowflake) =
        flameCache.getIfPresent(guildId) ?: flameCache.let {
            val userStats = cacheManager.getUsersReport(guildId)
            val newConfig =  FlameConfig(
                max(2, (userStats?.activeUsers ?: 0) / 10),
                max(4, (userStats?.activeUsers ?: 0) / 4),
                db.flameScope.getFlame(guildId.toString()),
                (userStats?.averageMessageInterval ?: 0.0).toDuration(DurationUnit.SECONDS)
            )
            it.put(guildId, newConfig)
            newConfig
        }
    override fun register() {
        kord.on<ReactionAddEvent> {
            if(isAllowed(guildId, message) && emoji.name == flame && message.asMessage().author?.isBot == false && guildId != null) {
                val flameConfig = getFlameParameters(guildId!!)
                val reactionMessage = message.asMessage()
                val reactionCount = reactionMessage.reactions.firstOrNull { it.emoji.name == flame }?.count ?: 0
                if (reactionCount == flameConfig.activationThreshold) db.flameScope.addToFlameCount(guildId!!.toString())
                else if (reactionCount == flameConfig.saveThreshold && message.asMessage().content.isNotBlank() && message.asMessage().content.length <= 300)
                    db.flameScope.addFlame(guildId!!.toString(), message.asMessage().content)

                lastReactions.getIfPresent(guildId!!)?.let { lastFlame ->
                    if ((reactionMessage.timestamp - lastFlame) < flameConfig.meanInterval) {
                        messages[guildId!!]?.let { msgList ->
                            msgList.firstOrNull()?.let { lastMsg ->
                                lastMsg.reply {
                                    content = flameConfig.sentences.random()
                                }
                                messages[guildId!!] = msgList.drop(1)
                            }
                        }
                    }
                }

                lastReactions.put(guildId!!, reactionMessage.timestamp)
                (messages[guildId!!] ?: emptyList()).let {
                    if(!it.contains(reactionMessage)) {
                        messages[guildId!!] = it + reactionMessage
                    }
                }
            }
        }
    }

}