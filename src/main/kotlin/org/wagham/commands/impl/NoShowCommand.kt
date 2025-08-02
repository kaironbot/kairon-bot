package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.core.Kord
import org.wagham.annotations.BotCommand
import org.wagham.commands.SlashCommandWithSubcommands
import org.wagham.components.CacheManager
import org.wagham.config.locale.commands.NoShowLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.utils.defaultLocale

@BotCommand("all")
class NoShowCommand(
	kord: Kord,
	db: KabotMultiDBClient,
	cacheManager: CacheManager
) : SlashCommandWithSubcommands(kord, db, cacheManager) {

	override val commandName = "noshow"
	override val defaultDescription = NoShowLocale.DESCRIPTION.locale(defaultLocale)
	override val localeDescriptions: Map<Locale, String> = NoShowLocale.DESCRIPTION.localeMap

}