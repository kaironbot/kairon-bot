package org.wagham.components

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.entity.Snowflake
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.ExpTable
import java.util.concurrent.TimeUnit

class CacheManager(private val db: KabotMultiDBClient) {

    private val expTableCache: Cache<Snowflake, ExpTable> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build()

    suspend fun getExpTable(guildId: Snowflake): ExpTable =
        expTableCache.getIfPresent(guildId) ?:
            db.utilityScope.getExpTable(guildId.toString()).also {
                expTableCache.put(guildId, it)
            }

}