package org.wagham.events

import dev.kord.core.Kord
import org.wagham.components.CacheManager
import org.wagham.db.KabotMultiDBClient

interface Event {
    val kord: Kord
    val db: KabotMultiDBClient
    val cacheManager: CacheManager
    val eventId: String

    fun register()
}