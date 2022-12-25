package org.wagham.commands

import dev.kord.core.Kord
import org.wagham.components.CacheManager
import org.wagham.db.KabotMultiDBClient

interface Command {
    val kord: Kord
    val db: KabotMultiDBClient
    val cacheManager: CacheManager
    val commandName: String

    suspend fun registerCommand()
    fun registerCallback()
}