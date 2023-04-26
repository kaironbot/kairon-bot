package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import kotlinx.coroutines.flow.toList
import org.wagham.annotations.BotCommand
import org.wagham.commands.SimpleResponseSlashCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.commands.HelpLocale
import org.wagham.db.KabotMultiDBClient

@BotCommand("all")
class HelpCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SimpleResponseSlashCommand() {

    override val commandName = "help"
    override val defaultDescription = "Show info about the commands of this bot"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Show info about the commands of this bot",
        Locale.ITALIAN to "Mostra i comandi di questo bot"
    )

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            defaultDescription
        ) {
            localeDescriptions.forEach{ (locale, description) ->
                description(locale, description)
            }
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val commands = kord.getGlobalApplicationCommands(true).toList().sortedBy { it.name }
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        return fun InteractionResponseModifyBuilder.() {
            embed {
                title = HelpLocale.TITLE.locale(locale)
                description = buildString {
                    commands.forEach {
                        append("</${it.name}:${it.id}> ")
                        append(it.data.descriptionLocalizations.value?.get(Locale.fromString(locale)) ?: "")
                        append("\n\n")
                    }
                }
                color = Colors.DEFAULT.value
            }
        }
    }

}