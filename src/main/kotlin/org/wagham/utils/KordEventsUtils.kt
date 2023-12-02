package org.wagham.utils

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.User
import dev.kord.core.entity.interaction.SelectMenuInteraction
import dev.kord.core.event.interaction.SelectMenuInteractionCreateEvent
import dev.kord.core.on

data class SelectMenuContext(
    val interaction: SelectMenuInteraction,
    val guildId: Snowflake,
    val locale: String,
    val responsible: User
)

/**
 * Wrapper around kord listener for [SelectMenuInteractionCreateEvent]. If the interaction satisfies the parameters
 * specified in the condition, then the block is executed in the coroutine scope defined by the on function.
 *
 * @param condition a function that receives a [SelectMenuInteractionCreateEvent] and returns true if this listener
 * should be executed.
 * @param block a function that received a [SelectMenuContext] and executes the logic of the listener.
 */
fun Kord.onSelection(condition: SelectMenuInteractionCreateEvent.() -> Boolean, block: suspend SelectMenuContext.() -> Unit) = on<SelectMenuInteractionCreateEvent> {
    val params = interaction.extractCommonParameters()
    if(condition(this)) {
        block(SelectMenuContext(interaction, params.guildId, params.locale, params.responsible))
    }
}