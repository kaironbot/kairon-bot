package org.wagham.components

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.entity.Snowflake
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.ExpTable
import org.wagham.db.models.ScheduledEvent
import org.wagham.db.models.ServerConfig
import org.wagham.utils.ActiveUsersReport
import java.util.concurrent.TimeUnit

class CacheManager(
    val db: KabotMultiDBClient,
    val schedulingManager: SchedulingManager,
    val profile: String
) {

    @PublishedApi
    internal val collectionCacheConfig: MutableMap<String, suspend (Snowflake, KabotMultiDBClient) -> Collection<Any>> = mutableMapOf()

    @PublishedApi
    internal val collectionCaches: MutableMap<String, Cache<Snowflake, Collection<Any>>> = mutableMapOf()

    private val expTableCache: Cache<Snowflake, ExpTable> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build()

    private val serverConfigCache: Cache<Snowflake, ServerConfig> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build()

    private val activeUsersCache: Cache<Snowflake, ActiveUsersReport> =
        Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.DAYS)
            .build()

    private val guildCommands = mutableListOf<String>()
    private val guildEvents = mutableListOf<String>()

    suspend fun getExpTable(guildId: Snowflake): ExpTable =
        expTableCache.getIfPresent(guildId) ?:
            db.utilityScope.getExpTable(guildId.toString()).also {
                println(guildId)
                println(it)
                expTableCache.put(guildId, it)
            }


    suspend fun getConfig(guildId: Snowflake, bypassCache: Boolean = false): ServerConfig =
        serverConfigCache.getIfPresent(guildId)?.takeIf { !bypassCache }
            ?: db.serverConfigScope.getGuildConfig(guildId.toString())

    suspend fun setConfig(guildId: Snowflake, config: ServerConfig) =
        db.serverConfigScope.setGuildConfig(guildId.toString(), config).also {
            serverConfigCache.put(
                guildId,
                db.serverConfigScope.getGuildConfig(guildId.toString())
            )
        }

    inline fun <reified T> createNewCollectionCache(noinline updateLambda: suspend (Snowflake, KabotMultiDBClient) -> Collection<Any>) =
        T::class.qualifiedName?.also {
            collectionCacheConfig[it] = updateLambda
            collectionCaches[it] = Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build()
        } ?: throw IllegalAccessError("Cannot create cache for ${T::class}")

    suspend inline fun <reified T> getCollectionOfType(guildId: String): Collection<T> =
        getCollectionOfType(Snowflake(guildId))

    suspend inline fun <reified T> getCollectionOfType(guildId: Snowflake): Collection<T> =
        T::class.qualifiedName?.let {
            collectionCaches[it]?.let { cache ->
                cache.getIfPresent(guildId) ?:
                    (collectionCacheConfig[it]?.let { config ->
                       config(guildId, db).also { elements ->
                           cache.put(guildId, elements)
                       }
                    } ?: throw IllegalAccessError("No config for $it cache"))
            }?.let { elements -> elements as Collection<T> }
        } ?: throw IllegalAccessError("Cannot get elements for ${T::class}")

    fun getCommands() = guildCommands.toList()

    fun registerCommand(command: String) = guildCommands.add(command)

    fun getEvents() = guildEvents.toList()

    fun registerEvent(event: String) = guildEvents.add(event)

    fun getUsersReport(guildId: Snowflake) = activeUsersCache.getIfPresent(guildId)

    fun storeUsersReport(guildId: Snowflake, usersReport: ActiveUsersReport) =
        activeUsersCache.put(guildId, usersReport)

    suspend fun scheduleEvent(guildId: Snowflake, event: ScheduledEvent) {
        db.scheduledEventsScope.addScheduledEvent(guildId.toString(), event)
        schedulingManager.addToQueue(guildId, event)
    }

}