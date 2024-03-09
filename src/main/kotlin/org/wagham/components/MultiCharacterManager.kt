package org.wagham.components

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.entity.ButtonStyle
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.User
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.SelectMenuInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.component.option
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.actionRow
import dev.kord.rest.builder.message.embed
import kotlinx.coroutines.flow.toList
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.components.MultiCharacterLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Character
import org.wagham.entities.CharactersOrSelectionMessage
import org.wagham.entities.InteractionParameters
import org.wagham.utils.compactUuid
import org.wagham.utils.extractCommonParameters
import org.wagham.utils.updateWithExpirationError
import org.wagham.utils.respondWithForbiddenError
import java.util.concurrent.TimeUnit

class MultiCharacterManager<T>(
    val db: KabotMultiDBClient,
    val kord: Kord,
    private val command: MultiCharacterCommand<T>
) {
    private val interactionPrefix = "${command::class.qualifiedName}.multi"

    init {
        handleButton()
        handleSelection()
    }

    companion object {
        private const val select = "select"
        private const val confirm = "confirm"

        data class NextSelection(
            val user: User,
            val characters: List<Character>
        )
        data class CharacterSelectionData<T>(
            val responsible: User,
            val context: T,
            val sourceCharacter: Character? = null,
            val toBeSelected: Map<User, List<Character>> = emptyMap(),
            val selected: List<Character> = emptyList(),
            val nextSelection: String? = null
        ) {

            fun addToSelected(character: Character) = copy(
                selected = selected + character
            )

            fun addToSelected(characterId: String) = toBeSelected.entries.first { (_, characters) ->
                characters.any { it.id == characterId }
            }.let { (user, characters) ->
                copy(
                    toBeSelected = toBeSelected - user,
                    selected = selected + characters.first { it.id == characterId },
                    nextSelection = null
                )
            }

            fun addToBeSelected(user: User, characters: List<Character>) = copy(
                toBeSelected = toBeSelected + (user to characters)
            )
            fun nextSelectionOption(): NextSelection? =
                toBeSelected.entries.firstOrNull()?.let { NextSelection(it.key, it.value) }
        }
    }

    private val interactionCache: Cache<String, CharacterSelectionData<T>> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build()

    private fun handleSelection() = kord.on<SelectMenuInteractionCreateEvent> {
        if (interaction.componentId.startsWith(interactionPrefix) && interaction.componentId.contains(select)) {
            val (_, id, _) = interaction.componentId.split(defaultSeparator)
            val data = interactionCache.getIfPresent(id)
            val params = interaction.extractCommonParameters()
            val selection = interaction.values.firstOrNull()
            when {
                data == null -> interaction.updateWithExpirationError(params.locale)
                data.responsible != params.responsible -> interaction.respondWithForbiddenError(params.locale)
                selection != null -> {
                    interactionCache.put(id, data.copy(nextSelection = selection))
                    interaction.deferEphemeralMessageUpdate()
                }
                else -> interaction.deferEphemeralMessageUpdate()
            }
        }
    }

    private fun handleButton() = kord.on<ButtonInteractionCreateEvent> {
        if (interaction.componentId.startsWith(interactionPrefix) && interaction.componentId.contains(confirm)) {
            val (_, id, _) = interaction.componentId.split(defaultSeparator)
            val data = interactionCache.getIfPresent(id)
            val params = interaction.extractCommonParameters()
            when {
                data == null -> interaction.updateWithExpirationError(params.locale)
                data.responsible != params.responsible -> interaction.respondWithForbiddenError(params.locale)
                data.nextSelection != null -> {
                    val updatedData = data.addToSelected(data.nextSelection)
                    updatedData.nextSelectionOption()?.let { nextSelection ->
                        interactionCache.put(id, updatedData)
                        interaction.deferPublicMessageUpdate().edit(
                            buildNextSelectionMessage(
                                id, params.locale, nextSelection.user, nextSelection.characters
                            )
                        )
                    } ?: command.multiCharacterAction(interaction, updatedData.selected, data.context, data.sourceCharacter)
                }
                else -> interaction.deferEphemeralResponse()
            }
        }
    }

    /**
     * Gets the active characters for the list of users passed as parameters.
     * If all the users have one character, then returns the list of character, otherwise starts an interaction to
     * select among multiple characters.
     * @param players the list of players to get the characters for.
     * @param sourceCharacter the character that is the origin of the interaction (to give items or money), if applicable.
     * @param context all the additional data needed to perform the interaction, they will be passed back to the original command.
     * @param params an [InteractionParameters] extracted from the event.
     * @return [CharactersOrSelectionMessage]
     */
    suspend fun startSelectionOrReturnCharacters(players: List<User>, sourceCharacter: Character?, context: T, params: InteractionParameters): CharactersOrSelectionMessage {
        val selectionData = players.fold(CharacterSelectionData(params.responsible, context, sourceCharacter)) { acc, user ->
            val characters = db.charactersScope.getActiveCharacters(params.guildId.toString(), user.id.toString()).toList()
            if (characters.isEmpty()) {
                throw NoActiveCharacterException(user.id.toString())
            } else if (characters.size == 1) {
                acc.addToSelected(characters.first())
            } else {
                acc.addToBeSelected(user, characters)
            }
        }
        return selectionData.nextSelectionOption()?.let { nextSelection ->
            val interactionId = compactUuid()
            interactionCache.put(interactionId, selectionData)
            CharactersOrSelectionMessage(
                response = buildNextSelectionMessage(
                    interactionId,
                    params.locale,
                    nextSelection.user,
                    nextSelection.characters
                )
            )
        } ?: CharactersOrSelectionMessage(characters = selectionData.selected)
    }

    private fun buildNextSelectionMessage(
        interactionId: String,
        locale: String,
        user: User,
        characterOptions: List<Character>
    ) = fun InteractionResponseModifyBuilder.() {
        embed {
            title = "${user.username} ${MultiCharacterLocale.TITLE.locale(locale)}"
            description = MultiCharacterLocale.DESCRIPTION.locale(locale)
            color = Colors.DEFAULT.value
        }
        actionRow {
            stringSelect("$interactionPrefix$defaultSeparator$interactionId$defaultSeparator$select") {
                characterOptions.forEach {
                    option(it.name, it.id)
                }
            }
        }
        actionRow {
            interactionButton(
                ButtonStyle.Primary,
                "$interactionPrefix$defaultSeparator$interactionId$defaultSeparator$confirm"
            ) {
                label = CommonLocale.CONFIRM.locale(locale)
            }
        }
    }

}