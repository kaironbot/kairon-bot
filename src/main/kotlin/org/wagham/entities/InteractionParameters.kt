package org.wagham.entities

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.User

data class InteractionParameters(
    val guildId: Snowflake,
    val locale: String,
    val responsible: User
)