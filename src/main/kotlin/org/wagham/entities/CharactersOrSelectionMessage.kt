package org.wagham.entities

import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.db.models.Character

data class CharactersOrSelectionMessage(
    val characters: List<Character>? = null,
    val response: (InteractionResponseModifyBuilder.() -> Unit)? = null
)