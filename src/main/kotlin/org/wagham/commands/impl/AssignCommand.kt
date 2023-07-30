package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.core.Kord
import org.wagham.annotations.BotCommand
import org.wagham.commands.SlashCommandWithSubcommands
import org.wagham.components.CacheManager
import org.wagham.config.locale.commands.AssignLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.utils.defaultLocale

@BotCommand("all")
class AssignCommand(
    kord: Kord,
    db: KabotMultiDBClient,
    cacheManager: CacheManager
) : SlashCommandWithSubcommands(kord, db, cacheManager) {

    override val commandName = "assign"
    override val defaultDescription = AssignLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = AssignLocale.DESCRIPTION.localeMap

}