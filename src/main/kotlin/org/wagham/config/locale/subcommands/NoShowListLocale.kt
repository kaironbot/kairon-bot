package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class NoShowListLocale(val localeMap: Map<Locale, String>): LocaleEnum {
	DESCRIPTION(
		mapOf(
			Locale.ENGLISH_GREAT_BRITAIN to "List the strikes assigned to a player",
			Locale.ITALIAN to "Elenca gli strike assegnati a un giocatore"
		)
	),
	NO_STRIKES(
		mapOf(
			Locale.ENGLISH_GREAT_BRITAIN to "This player has no strikes",
			Locale.ITALIAN to "Questo giocatore non ha strike"
		)
	),
	TARGET(
		mapOf(
			Locale.ENGLISH_GREAT_BRITAIN to "The player to give the strike to",
			Locale.ITALIAN to "Il giocatore a cui assegnare lo strike"
		)
	),
	TITLE(
		mapOf(
			Locale.ENGLISH_GREAT_BRITAIN to "Strikes for player: ",
			Locale.ITALIAN to "Strike assegnati a: "
		)
	);
	override fun locale(language: String) = locale(Locale.fromString(language))
	override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}