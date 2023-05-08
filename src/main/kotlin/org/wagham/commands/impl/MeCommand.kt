package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import org.wagham.annotations.BotCommand
import org.wagham.commands.SimpleResponseSlashCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.commands.MSLocale
import org.wagham.config.locale.commands.MeLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.createGenericEmbedError

@BotCommand("all")
class MeCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SimpleResponseSlashCommand() {

    override val commandName = "me"
    override val defaultDescription = "Show information about your character"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Show information about your character",
        Locale.ITALIAN to "Mostra informazioni sul tuo personaggio"
    )

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

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value ?: throw GuildNotFoundException()
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val expTable = cacheManager.getExpTable(guildId)
        val target = event.interaction.command.users["target"]?.id ?: event.interaction.user.id
        return try {
            val character = db.charactersScope.getActiveCharacter(guildId.toString(), target.toString())
            fun InteractionResponseModifyBuilder.() {
                embed {
                    color = Colors.DEFAULT.value
                    title = character.name

                    field {
                        name = MeLocale.RACE.locale(locale)
                        value = "${character.race}"
                        inline = true
                    }
                    field {
                        name = MeLocale.CLASS.locale(locale)
                        value = "${character.characterClass}"
                        inline = true
                    }
                    field {
                        name = MeLocale.ORIGIN.locale(locale)
                        value = "${character.territory}"
                        inline = true
                    }

                    field {
                        name = "Exp"
                        value = "${character.ms()}"
                        inline = true
                    }
                    field {
                        name = MeLocale.LEVEL.locale(locale)
                        value = expTable.expToLevel(character.ms().toFloat())
                        inline = true
                    }
                    field {
                        name = "Tier"
                        value = expTable.expToTier(character.ms().toFloat())
                        inline = true
                    }

                    field {
                        name = MeLocale.LANGUAGES.locale(locale)
                        value = character.languages.takeIf { it.isNotEmpty() }?.joinToString(separator = ", ") { it.name }
                            ?: MeLocale.NO_LANGUAGES.locale(locale)
                        inline = true
                    }
                    field {
                        name = MeLocale.TOOLS.locale(locale)
                        value = character.proficiencies.takeIf { it.isNotEmpty() }?.joinToString(separator = ", ") { it.name }
                            ?: MeLocale.NO_TOOLS.locale(locale)
                        inline = true
                    }
                }
            }
        } catch (e: NoActiveCharacterException) {
            createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
        }
    }
}