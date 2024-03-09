package org.wagham.commands.impl

import com.github.h0tk3y.betterParse.grammar.parseToEnd
import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.embed
import org.wagham.annotations.BotCommand
import org.wagham.commands.SimpleResponseSlashCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.commands.RollLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.parsers.DiceRollParser
import org.wagham.utils.defaultLocale

@BotCommand("all")
class RollCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SimpleResponseSlashCommand() {

    override val commandName = "roll"
    override val defaultDescription = RollLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = RollLocale.DESCRIPTION.localeMap
    private val parser = DiceRollParser()

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            defaultDescription
        ) {
            localeDescriptions.forEach{ (locale, description) ->
                description(locale, description)
            }
            string("formula", RollLocale.FORMULA.locale("en")) {
                RollLocale.FORMULA.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = true
            }
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val formula = event.interaction.command.strings["formula"]
            ?.lowercase()
            ?.replace(Regex("[^0-9d+\\-]"), "")
            ?: throw IllegalStateException("No formula provided")
        val result = parser.parseToEnd(formula)
        return fun InteractionResponseModifyBuilder.() {
            embed {
                title = "${RollLocale.YOU_ROLLED.locale(locale)}: ${result.total}"
                color = Colors.DEFAULT.value
                description = buildString {
                    append("**Formula: $formula**\n")
                    append("Rolls:\n")
                    result.results.forEach { (diceType, rolls) ->
                        append("d$diceType: ")
                        append(rolls.joinToString(", "))
                        append("\n")
                    }

                }
            }
        }
    }


}