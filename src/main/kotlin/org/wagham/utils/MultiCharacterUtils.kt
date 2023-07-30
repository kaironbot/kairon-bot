package org.wagham.utils

import dev.kord.core.entity.User
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.commands.Command
import org.wagham.config.locale.CommonLocale
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Character
import org.wagham.entities.InteractionParameters

suspend fun Command<InteractionResponseModifyBuilder>.withOneActiveCharacterOrErrorMessage(
    user: User,
    params: InteractionParameters,
    block: suspend (Character) -> (InteractionResponseModifyBuilder.() -> Unit)
): (InteractionResponseModifyBuilder.() -> Unit) {
    val characters = db.charactersScope.getActiveCharacterOrAllActive(params.guildId.toString(), user.id.toString())
    return when {
        characters.currentActive == null && characters.allActive.isEmpty() ->
            createGenericEmbedError(
                "<@!${user.id}> ${CommonLocale.NO_ACTIVE_CHARACTER.locale(params.locale)}"
            )
        characters.currentActive == null ->
            createGenericEmbedError(CommonLocale.MULTIPLE_CHARACTERS.locale(params.locale))
        else -> try {
            block(characters.currentActive!!)
        } catch (e: NoActiveCharacterException) {
            createGenericEmbedError(
                "<@!${e.playerId}> ${CommonLocale.NO_ACTIVE_CHARACTER.locale(params.locale)}"
            )
        }
    }
}