package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.core.Kord
import org.wagham.annotations.BotCommand
import org.wagham.commands.SlashCommandWithSubcommands
import org.wagham.components.CacheManager
import org.wagham.config.locale.commands.CharacterLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.utils.defaultLocale

@BotCommand("all")
class CharacterCommand (
    kord: Kord,
    db: KabotMultiDBClient,
    cacheManager: CacheManager
) : SlashCommandWithSubcommands(kord, db, cacheManager) {

    override val commandName = "character"
    override val defaultDescription = CharacterLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = CharacterLocale.DESCRIPTION.localeMap
}