package org.wagham.config.locale.commands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class ExpLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    LEVEL(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Level",
            Locale.ITALIAN to "Livello"
        )
    ),
    NO_ACTIVE_CHARACTERS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The following players have no active character:",
            Locale.ITALIAN to "I seguenti giocatori non hanno personaggi attivi:"
        )
    ),
    TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "A user to show the MS for the active character",
            Locale.ITALIAN to "Un utente per cui mostrare le MS e il livello"
        )
    ),
    TITLE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Details of the active characters",
            Locale.ITALIAN to "Informazioni sui personaggi attivi"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}