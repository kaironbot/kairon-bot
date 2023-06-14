package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.core.Kord
import org.wagham.annotations.BotCommand
import org.wagham.commands.SlashCommandWithSubcommands
import org.wagham.components.CacheManager
import org.wagham.db.KabotMultiDBClient

@BotCommand("all")
class LanguageCommand (
    kord: Kord,
    db: KabotMultiDBClient,
    cacheManager: CacheManager
) : SlashCommandWithSubcommands(kord, db, cacheManager) {

    override val commandName = "language"
    override val defaultDescription = "Manage languages"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Manage languages",
        Locale.ITALIAN to "Gestisci i linguaggi"
    )
}