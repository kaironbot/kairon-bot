package org.wagham.entities.channels

import kotlinx.serialization.Serializable

@Serializable
data class ExpUpdate(
    val guildId: String,
    val updates: Map<String, Float>
)