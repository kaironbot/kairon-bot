package org.wagham.entities.channels

import dev.kord.common.entity.Snowflake
import kotlinx.serialization.Serializable

@Serializable
data class UpdateGuildAttendanceMessage(
    val guildId: Snowflake,
    val operation: UpdateGuildAttendanceOperation? = null,
    val playerId: String? = null
)

enum class UpdateGuildAttendanceOperation(val id: String) {
    ABORT_AFTERNOON("abort-afternoon"),
    ABORT_EVENING("abort-evening"),
    REGISTER_AFTERNOON("register-afternoon"),
    REGISTER_EVENING("register-evening")
}
