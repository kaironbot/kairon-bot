package org.wagham.components

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.entity.Snowflake
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.ExpTable
import org.wagham.db.models.ServerConfig
import java.util.concurrent.TimeUnit

class CacheManager(private val db: KabotMultiDBClient) {

    private val expTableCache: Cache<Snowflake, ExpTable> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build()

    private val serverConfigCache: Cache<Snowflake, ServerConfig> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build()

    suspend fun getExpTable(guildId: Snowflake): ExpTable =
        expTableCache.getIfPresent(guildId) ?:
            db.utilityScope.getExpTable(guildId.toString()).also {
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


}