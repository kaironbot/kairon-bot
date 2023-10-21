package org.wagham.entities.channels

import kotlinx.serialization.Serializable

@Serializable
data class RegisteredSession(
    val guildId: String,
    val sessionId: Int
)