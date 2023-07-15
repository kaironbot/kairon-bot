package org.wagham.utils

import dev.kord.core.behavior.interaction.ComponentInteractionBehavior
import dev.kord.core.behavior.interaction.response.respond

suspend fun <T: ComponentInteractionBehavior> replyOnError(interaction: T, block: suspend (T) -> Unit) {
    try {
        block(interaction)
    } catch (e: Exception) {
        interaction.deferPublicResponse().respond(
            createGenericEmbedError(e.message ?: e.stackTraceToString())
        )
    }
}