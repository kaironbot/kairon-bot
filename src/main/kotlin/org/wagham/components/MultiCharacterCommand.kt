package org.wagham.components

import dev.kord.core.entity.interaction.ComponentInteraction
import org.wagham.db.models.Character

interface MultiCharacterCommand<T> {

    suspend fun multiCharacterAction(interaction: ComponentInteraction, characters: List<Character>, context: T, sourceCharacter: Character? = null)

}