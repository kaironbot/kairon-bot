package org.wagham.entities

import dev.kord.common.entity.Snowflake

data class InteractionParameters(
    val guildId: Snowflake,
    val locale: String
)