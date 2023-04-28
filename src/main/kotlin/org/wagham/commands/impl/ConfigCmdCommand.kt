package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.core.Kord
import org.wagham.annotations.BotCommand
import org.wagham.commands.SlashCommandWithSubcommands
import org.wagham.components.CacheManager
import org.wagham.db.KabotMultiDBClient

@BotCommand("all")
class ConfigCmdCommand(
    kord: Kord,
    db: KabotMultiDBClient,
    cacheManager: CacheManager
) : SlashCommandWithSubcommands(kord, db, cacheManager) {

    override val commandName = "config_command"
    override val defaultDescription = "Config the access to a command"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Config the access to a command",
        Locale.ITALIAN to "Configura l'accesso a un comando"
    )

}