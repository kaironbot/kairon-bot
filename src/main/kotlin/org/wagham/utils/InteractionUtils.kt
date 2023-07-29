package org.wagham.utils

import dev.kord.core.behavior.interaction.ComponentInteractionBehavior
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import org.wagham.config.locale.CommonLocale
import org.wagham.entities.InteractionParameters
import org.wagham.exceptions.GuildNotFoundException

suspend fun <T: ComponentInteractionBehavior> replyOnError(interaction: T, block: suspend (T) -> Unit) {
    try {
        block(interaction)
    } catch (e: Exception) {
        interaction.deferPublicResponse().respond(
            createGenericEmbedError(e.message ?: e.stackTraceToString())
        )
    }
}

suspend fun ComponentInteractionBehavior.respondWithExpirationError(locale: String) =
    deferEphemeralMessageUpdate().edit(createGenericEmbedError(CommonLocale.INTERACTION_EXPIRED.locale(locale)))

suspend fun ComponentInteractionBehavior.respondWithForbiddenError(locale: String) =
    deferEphemeralResponse().respond(createGenericEmbedError(CommonLocale.INTERACTION_STARTED_BY_OTHER.locale(locale)))

suspend fun ComponentInteractionBehavior.respondWithGenericError(locale: String) =
    deferEphemeralMessageUpdate().edit(createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale)))

fun GuildChatInputCommandInteractionCreateEvent.extractCommonParameters() = InteractionParameters(
    interaction.data.guildId.value ?: throw GuildNotFoundException(),
    interaction.locale?.language ?: interaction.guildLocale?.language ?: "en",
    interaction.user
)

fun ComponentInteraction.extractCommonParameters() = InteractionParameters(
    data.guildId.value ?: throw GuildNotFoundException(),
    locale?.language ?: guildLocale?.language ?: "en",
    user
)