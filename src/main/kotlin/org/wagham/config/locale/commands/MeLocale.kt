package org.wagham.config.locale.commands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class MeLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    CLASS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Class",
            Locale.ITALIAN to "Classe"
        )
    ),
    LANGUAGES(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Languages",
            Locale.ITALIAN to "Linguaggi"
        )
    ),
    LEVEL(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Level",
            Locale.ITALIAN to "Livello"
        )
    ),
    NO_LANGUAGES(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "This character does not speak any language.",
            Locale.ITALIAN to "Questo personaggio non parla alcun linguaggio."
        )
    ),
    NO_TOOLS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "This character has no proficiency in any tool.",
            Locale.ITALIAN to "Questo personaggio non ha competenza in alcuno strumento."
        )
    ),
    ORIGIN(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Origin",
            Locale.ITALIAN to "Provenienza"
        )
    ),
    RACE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Race",
            Locale.ITALIAN to "Razza"
        )
    ),
    TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "A user to show the info for the active character",
            Locale.ITALIAN to "Un utente per cui mostrare le informazioni"
        )
    ),
    TOOLS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Tool proficiencies",
            Locale.ITALIAN to "Competenze negli strumenti"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}