package org.wagham.commands

import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import org.wagham.annotations.BotCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.LocaleEnum
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.createGenericEmbedError

@BotCommand("wagham")
class MSCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SlashCommand() {

    override val commandName = "ms"
    override val commandDescription = "Shows your level and ms"

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            commandDescription
        ) {
            user("target", "The user to show the MS for the active character") {
                required = false
                autocomplete = true
            }
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value ?: throw GuildNotFoundException()
        val expTable = cacheManager.getExpTable(guildId)
        val target = event.interaction.command.users["target"]?.id ?: event.interaction.user.id
        return try {
            val character = db.charactersScope.getActiveCharacter(guildId.toString(), target.toString())
            fun InteractionResponseModifyBuilder.() {
                embed {
                    color = Colors.DEFAULT.value
                    title = character.name
                    description = character.characterClass
                    field {
                        name = "MS"
                        value = "${character.ms()}"
                        inline = true
                    }
                    field {
                        name = "Level"
                        value = expTable.expToLevel(character.ms().toFloat())
                        inline = true
                    }
                    field {
                        name = "Tier"
                        value = expTable.expToTier(character.ms().toFloat())
                        inline = true
                    }
                }
            }
        } catch (e: NoActiveCharacterException) {
            createGenericEmbedError("Player has no active character")
        }
    }

}