package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.core.Kord
import org.wagham.annotations.BotCommand
import org.wagham.commands.SlashCommandWithSubcommands
import org.wagham.components.CacheManager
import org.wagham.db.KabotMultiDBClient

@BotCommand("all")
class ToolCommand (
    kord: Kord,
    db: KabotMultiDBClient,
    cacheManager: CacheManager
) : SlashCommandWithSubcommands(kord, db, cacheManager) {

    override val commandName = "tool"
    override val defaultDescription = "Manage tools proficiencies"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Manage tools proficiencies",
        Locale.ITALIAN to "Gestisci le competenze negli strumenti"
    )
}