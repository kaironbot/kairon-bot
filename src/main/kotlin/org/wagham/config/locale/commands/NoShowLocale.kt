package org.wagham.config.locale.commands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class NoShowLocale(val localeMap: Map<Locale, String>): LocaleEnum {
	DESCRIPTION(
		mapOf(
			Locale.ENGLISH_GREAT_BRITAIN to "Manage plauers strikes",
			Locale.ITALIAN to "Gestisci gli strike assegnati ai giocatori"
		)
	);
	override fun locale(language: String) = locale(Locale.fromString(language))
	override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}