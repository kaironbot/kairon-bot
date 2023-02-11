package org.wagham.components

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.entity.Snowflake
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.ExpTable
import org.wagham.db.models.Proficiency
import org.wagham.db.models.ServerConfig
import org.wagham.utils.ActiveUsersReport
import java.util.concurrent.TimeUnit

class CacheManager(
    val db: KabotMultiDBClient,
    val profile: String
) {

    private val expTableCache: Cache<Snowflake, ExpTable> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build()

    private val proficienciesCache: Cache<Snowflake, List<Proficiency>> =
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
                expTableCache.put(guildId, it)
            }

    suspend fun getProficiencies(guildId: Snowflake): List<Proficiency> =
        proficienciesCache.getIfPresent(guildId) ?:
            db.utilityScope.getProficiencies(guildId.toString()).also {
                proficienciesCache.put(guildId, it)
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

    fun getCommands() = guildCommands.toList()

    fun registerCommand(command: String) = guildCommands.add(command)

    fun getEvents() = guildEvents.toList()

    fun registerEvent(event: String) = guildEvents.add(event)

    fun getUsersReport(guildId: Snowflake) = activeUsersCache.getIfPresent(guildId)

    fun storeUsersReport(guildId: Snowflake, usersReport: ActiveUsersReport) =
        activeUsersCache.put(guildId, usersReport)

}