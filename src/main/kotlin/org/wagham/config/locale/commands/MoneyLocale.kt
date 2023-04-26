package org.wagham.config.locale.commands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class MoneyLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    MONEY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Current balance of:",
            Locale.ITALIAN to "Monete di:"
        )
    ),
    TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "A user to show the money for the active character",
            Locale.ITALIAN to "Un utente per cui mostrare le monete"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}