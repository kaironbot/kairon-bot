package org.wagham.config.locale.commands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class MSLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    LEVEL(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Level",
            Locale.ITALIAN to "Livello"
        )
    ),
    TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "A user to show the MS for the active character",
            Locale.ITALIAN to "Un utente per cui mostrare le MS e il livello"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}