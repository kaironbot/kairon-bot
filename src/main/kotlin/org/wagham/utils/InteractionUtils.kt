package org.wagham.utils

import dev.kord.core.behavior.interaction.ComponentInteractionBehavior
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.core.entity.interaction.ModalSubmitInteraction
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

/**
 * Defers the update of the interaction message and publishes an ephemeral update to communicate to the user that
 * the interaction they are referring is not valid anymore.
 *
 * @param locale a locale to format the message.
 */
suspend fun ComponentInteractionBehavior.updateWithExpirationError(locale: String) =
    deferEphemeralMessageUpdate().edit(createGenericEmbedError(CommonLocale.INTERACTION_EXPIRED.locale(locale)))

/**
 * Defers the response of the interaction message and publishes an ephemeral response to communicate to the user that
 * the interaction they are referring is not valid anymore.
 *
 * @param locale a locale to format the message.
 */
suspend fun ComponentInteractionBehavior.respondWithExpirationError(locale: String) =
    deferEphemeralResponse().respond(createGenericEmbedError(CommonLocale.INTERACTION_EXPIRED.locale(locale)))

suspend fun ComponentInteractionBehavior.operationCanceled(locale: String) =
    deferEphemeralMessageUpdate().edit(createGenericEmbedSuccess(CommonLocale.ABORTED.locale(locale)))

/**
 * Defers the update of the interaction message and publishes an ephemeral response to communicate to the user that they
 * are not authorized to interact with that message.
 *
 * @param locale a locale to format the message.
 */
suspend fun ComponentInteractionBehavior.updateWithForbiddenError(locale: String) =
    deferEphemeralMessageUpdate().edit(createGenericEmbedError(CommonLocale.INTERACTION_STARTED_BY_OTHER.locale(locale)))

/**
 * Defers the response of the interaction message and publishes an ephemeral update to communicate that contains a
 * custom error message, passed as parameter.
 *
 * @param message the message to publish.
 */
suspend fun ComponentInteractionBehavior.updateWithCustomError(message: String) =
    deferEphemeralMessageUpdate().edit(createGenericEmbedError(message))


/**
 * Defers the update of the interaction message and publishes an ephemeral response to communicate to the user that they
 * are not authorized to interact with that message.
 *
 * @param locale a locale to format the message.
 */
suspend fun ComponentInteractionBehavior.respondWithForbiddenError(locale: String) =
    deferEphemeralResponse().respond(createGenericEmbedError(CommonLocale.INTERACTION_STARTED_BY_OTHER.locale(locale)))

/**
 * Defers the response of the interaction message and publishes an ephemeral update to communicate a generic success
 *
 * @param locale a locale to format the message.
 */
suspend fun ComponentInteractionBehavior.updateWithGenericSuccess(locale: String) =
    deferEphemeralMessageUpdate().edit(createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale)))

/**
 * Defers the update of the interaction message and publishes an ephemeral update to communicate to the user that an
 * error happened.
 *
 * @param locale a locale to format the message.
 */
suspend fun ComponentInteractionBehavior.updateWithGenericError(locale: String) =
    deferEphemeralMessageUpdate().edit(createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale)))

/**
 * Defers the update of the interaction message and publishes an ephemeral response to communicate to the user that an
 * error happened.
 *
 * @param locale a locale to format the message.
 */
suspend fun ComponentInteractionBehavior.respondWithGenericError(locale: String) =
    deferEphemeralResponse().respond(createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale)))

/**
 * Defers the response of the interaction message and publishes an ephemeral response to communicate that contains a
 * custom error message, passed as parameter.
 *
 * @param message the message to publish.
 */
suspend fun ComponentInteractionBehavior.respondWithCustomError(message: String) =
    deferEphemeralResponse().respond(createGenericEmbedError(message))

/**
 * Defers the response of the interaction message and publishes an ephemeral response to communicate a generic success
 *
 * @param locale a locale to format the message.
 */
suspend fun ComponentInteractionBehavior.respondWithGenericSuccess(locale: String) =
    deferEphemeralResponse().respond(createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale)))

inline fun <R> withEventParameters(event: GuildChatInputCommandInteractionCreateEvent, block: InteractionParameters.() -> R) =
    with(event.extractCommonParameters(), block)

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

fun ModalSubmitInteraction.extractCommonParameters() = InteractionParameters(
    data.guildId.value ?: throw GuildNotFoundException(),
    locale?.language ?: guildLocale?.language ?: "en",
    user
)