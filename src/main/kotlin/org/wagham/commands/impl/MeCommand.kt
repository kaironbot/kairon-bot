package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import org.wagham.annotations.BotCommand
import org.wagham.commands.SimpleResponseSlashCommand
import org.wagham.components.CacheManager
import org.wagham.components.MultiCharacterCommand
import org.wagham.components.MultiCharacterManager
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.commands.MeLocale
import org.wagham.config.locale.components.MultiCharacterLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.Character
import org.wagham.entities.InteractionParameters
import org.wagham.utils.*

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
                    value = "${character.characterClass}"
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
            components = mutableListOf()
        }
    }
}