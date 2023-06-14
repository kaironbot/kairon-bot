package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class LanguageBuyLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    ALREADY_POSSESS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "This character already knows this language.",
            Locale.ITALIAN to "Questo personaggio conosce già questo linguaggio."
        )
    ),
    CANNOT_BUY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "This language cannot be bought.",
            Locale.ITALIAN to "Questo linguaggio non può essere acquistato."
        )
    ),
    MISSING_MATERIALS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "You need the following additional materials to buy this language",
            Locale.ITALIAN to "Ti mancano i seguenti materiali per acquistare questo linguaggio"
        )
    ),
    NOT_FOUND(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Language not found: ",
            Locale.ITALIAN to "Linguaggio non trovato: "
        )
    ),
    PROFICIENCY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The language to buy",
            Locale.ITALIAN to "Il linguaggio da acquistare"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}