package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.core.Kord
import org.wagham.annotations.BotCommand
import org.wagham.commands.SlashCommandWithSubcommands
import org.wagham.components.CacheManager
import org.wagham.db.KabotMultiDBClient

@BotCommand("all")
class AssignCommand(
    kord: Kord,
    db: KabotMultiDBClient,
    cacheManager: CacheManager
) : SlashCommandWithSubcommands(kord, db, cacheManager) {

    override val commandName = "assign"
    override val defaultDescription = "Assign money, items, and proficiencies to the players"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Assign money, items, and proficiencies to the players",
        Locale.ITALIAN to "Assegna monete, oggetti e competenze ai giocatori"
    )

}