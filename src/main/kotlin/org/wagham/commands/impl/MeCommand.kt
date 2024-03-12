package org.wagham.commands.impl

import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.Locale
import dev.kord.common.entity.ButtonStyle
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.ComponentInteractionBehavior
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.component.option
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.actionRow
import dev.kord.rest.builder.message.embed
import kotlinx.coroutines.flow.firstOrNull
import org.wagham.annotations.BotCommand
import org.wagham.commands.SimpleResponseSlashCommand
import org.wagham.components.CacheManager
import org.wagham.components.MultiCharacterCommand
import org.wagham.components.MultiCharacterManager
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.commands.MeLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.Character
import org.wagham.entities.InteractionParameters
import org.wagham.utils.*
import java.util.concurrent.TimeUnit

@BotCommand("all")
class MeCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SimpleResponseSlashCommand(), MultiCharacterCommand<Unit> {

    override val commandName = "me"
    override val defaultDescription = MeLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = MeLocale.DESCRIPTION.localeMap
    private val multiCharacterManager = MultiCharacterManager(db, kord, this)
    private val interactionCache = Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build<String, MulticlassInteractionData>()

    companion object {
        private const val CLASS_OPTION = "selectedClass"
        private const val CONFIRM = "confirm"

        private enum class MulticlassOperation { ADD, REMOVE }

        private data class MulticlassInteractionData(
            val character: Character,
            val operation: MulticlassOperation,
            val option: String? = null
        )
    }

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            defaultDescription
        ) {
            localeDescriptions.forEach{ (locale, description) ->
                description(locale, description)
            }
            user("target", MeLocale.TARGET.locale("en")) {
                MeLocale.TARGET.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = false
                autocomplete = true
            }
        }
        handleButtons()
        handleSelection()
    }

    /**
     * Handles the interaction generated when pressing the add class or remove class buttons.
     * It retrieves the playable resources and the character based on the player id and creation date.
     * It will fail if there is no class defined in the playable resources or if there is no corresponding character.
     */
    private fun handleButtons() = kord.on<ButtonInteractionCreateEvent> {
        if(MulticlassOperation.entries.any { verifyId(interaction.componentId, it.name) }) {
            val params = interaction.extractCommonParameters()
            val resources = db.utilityScope.getPlayableResources(params.guildId.toString())
            val (operation, player, creationDate) = extractComponentsFromComponentId(interaction.componentId)
            val character = db.charactersScope.getActiveCharacters(params.guildId.toString(), player).firstOrNull {
                it.created?.time == creationDate.toLong()
            }
            when {
                character == null -> interaction.respondWithCustomError(MeLocale.NO_CHARACTER.locale(params.locale))
                params.responsible.id.toString() != character.player -> interaction.respondWithCustomError(MeLocale.NOT_THE_OWNER.locale(params.locale))
                resources.classes.isEmpty() -> interaction.respondWithCustomError(MeLocale.NO_CLASS_DEFINED.locale(params.locale))
                MulticlassOperation.valueOf(operation) == MulticlassOperation.ADD ->
                    interaction.respondWithSelector(MulticlassOperation.ADD, character, resources.classes, params.locale)
                MulticlassOperation.valueOf(operation) == MulticlassOperation.REMOVE -> {
                    if(character.characterClass.size <= 1) interaction.respondWithCustomError(
                        MeLocale.ONLY_ONE_CLASS_ERROR.locale(params.locale)
                    )
                    else interaction.respondWithSelector(MulticlassOperation.REMOVE, character, resources.classes, params.locale)
                }
                else -> interaction.respondWithGenericError(params.locale)
            }
        }

        if(verifyId(interaction.componentId, CONFIRM)) {
            val params = interaction.extractCommonParameters()
            val (_, id) = extractComponentsFromComponentId(interaction.componentId)
            val data = interactionCache.getIfPresent(id)
            when {
                data == null -> interaction.updateWithExpirationError(params.locale)
                data.character.player != params.responsible.id.toString() -> interaction.updateWithForbiddenError(params.locale)
                data.option == null -> interaction.updateWithCustomError(MeLocale.NO_CLASS_SELECTED.locale(params.locale))
                else -> {
                    val newClasses = if(data.operation == MulticlassOperation.ADD)
                        data.character.characterClass + data.option
                    else data.character.characterClass - data.option
                    val result = db.charactersScope.updateCharacter(
                        params.guildId.toString(),
                        data.character.copy(characterClass = newClasses)
                    )
                    if(result) interaction.updateWithGenericSuccess(params.locale)
                    else interaction.updateWithGenericError(params.locale)
                }
            }
        }
    }

    /**
     * Listener for the selection event that updates the data stored in the cache with the selected value.
     */
    private fun handleSelection() = kord.onSelection({verifyId(interaction.componentId, CLASS_OPTION)}) {
        val (_, id) = extractComponentsFromComponentId(interaction.componentId)
        interactionCache.getIfPresent(id)?.also {
            interactionCache.put(id, it.copy(option = interaction.values.first()))
        }
        interaction.deferEphemeralMessageUpdate()
    }

    /**
     * Creates an ephemeral response that allows the user to select the class that they want to remove or add and then
     * confirm the operation.
     *
     * @param operation the type of [MulticlassOperation].
     * @param character the target [Character].
     * @param availableClasses a list of classes available in the guild.
     * @param locale the locale of the message.
     */
    private suspend fun ComponentInteractionBehavior.respondWithSelector(operation: MulticlassOperation, character: Character, availableClasses: List<String>, locale: String) {
        val options = if(operation == MulticlassOperation.ADD) {
            availableClasses.toSet() - character.characterClass.toSet()
        } else character.characterClass.toSet()
        val interactionId = compactUuid()
        interactionCache.put(
            interactionId,
            MulticlassInteractionData(character, operation)
        )
        deferEphemeralResponse().respond {
            embed {
                title = "${MeLocale.UPDATE_CHARACTER.locale(locale)}: **${character.name}**"
                description = if(operation == MulticlassOperation.ADD) MeLocale.ADD_MULTICLASS.locale(locale)
                    else MeLocale.REMOVE_MULTICLASS.locale(locale)
                color = Colors.DEFAULT.value
            }
            options.chunked(24).forEachIndexed { _, chunk ->
                actionRow {
                    stringSelect(buildElementId(CLASS_OPTION, interactionId)) {
                        chunk.forEach {
                            option(it, it)
                        }
                    }
                }
            }
            actionRow {
                interactionButton(ButtonStyle.Primary, buildElementId(CONFIRM, interactionId)) {
                    label = CommonLocale.CONFIRM.locale(locale)
                }
            }
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        return event.interaction.command.users["target"]?.takeIf { it.id != responsible.id }?.let {
            val targetOrSelectionContext = multiCharacterManager.startSelectionOrReturnCharacters(listOf(it), null, Unit, this)
            when {
                targetOrSelectionContext.characters != null -> generateEmbed(targetOrSelectionContext.characters.first(), this)
                targetOrSelectionContext.response != null -> targetOrSelectionContext.response
                else -> createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))
            }
        } ?: withOneActiveCharacterOrErrorMessage(responsible, this) {
            generateEmbed(it, this)
        }
    }

    override suspend fun multiCharacterAction(
        interaction: ComponentInteraction,
        characters: List<Character>,
        context: Unit,
        sourceCharacter: Character?
    ) {
        val params = interaction.extractCommonParameters()
        interaction.deferPublicMessageUpdate().edit(generateEmbed(characters.first(), params))
    }

    private suspend fun generateEmbed(character: Character, params: InteractionParameters): InteractionResponseModifyBuilder.() -> Unit {
        val expTable = cacheManager.getExpTable(params.guildId)
        return fun InteractionResponseModifyBuilder.() {
            embed {
                color = Colors.DEFAULT.value
                title = character.name

                field {
                    name = MeLocale.RACE.locale(params.locale)
                    value = "${character.race}"
                    inline = true
                }
                field {
                    name = MeLocale.CLASS.locale(params.locale)
                    value = character.characterClass.joinToString(", ")
                    inline = true
                }
                field {
                    name = MeLocale.ORIGIN.locale(params.locale)
                    value = "${character.territory}"
                    inline = true
                }

                field {
                    name = "Exp"
                    value = "${character.ms()}"
                    inline = true
                }
                field {
                    name = MeLocale.LEVEL.locale(params.locale)
                    value = expTable.expToLevel(character.ms().toFloat())
                    inline = true
                }
                field {
                    name = "Tier"
                    value = expTable.expToTier(character.ms().toFloat())
                    inline = true
                }

                field {
                    name = MeLocale.LANGUAGES.locale(params.locale)
                    value = character.languages.takeIf { it.isNotEmpty() }?.joinToString(separator = ", ") { it.name }
                        ?: MeLocale.NO_LANGUAGES.locale(params.locale)
                    inline = true
                }
                field {
                    name = MeLocale.TOOLS.locale(params.locale)
                    value = character.proficiencies.takeIf { it.isNotEmpty() }?.joinToString(separator = ", ") { it.name }
                        ?: MeLocale.NO_TOOLS.locale(params.locale)
                    inline = true
                }
                field {
                    name = MeLocale.BUILDINGS.locale(params.locale)
                    value = MeLocale.BUILDINGS_DESCRIPTION.locale(params.locale)
                    inline = false
                }
                character.buildings.forEach { (compositeId, buildings) ->
                    buildings.forEach { building ->
                        field {
                            name = building.name
                            value = compositeId.split(":").first()
                            inline = true
                        }
                    }
                }
            }
            if(character.player == params.responsible.id.toString()) {
                actionRow {
                    interactionButton(
                        ButtonStyle.Primary,
                        buildElementId(
                            MulticlassOperation.ADD.name,
                            character.player,
                            character.created?.time ?: System.currentTimeMillis()
                        )
                    ) {
                        label = MeLocale.ADD_MULTICLASS.locale(params.locale)
                    }
                    interactionButton(
                        ButtonStyle.Primary,
                        buildElementId(
                            MulticlassOperation.REMOVE.name,
                            character.player,
                            character.created?.time ?: System.currentTimeMillis()
                        )
                    ) {
                        label = MeLocale.REMOVE_MULTICLASS.locale(params.locale)
                    }
                }
            } else {
                components = mutableListOf()
            }
        }
    }
}