package org.wagham.config.locale.components

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class MultiCharacterLocale(val localeMap: Map<Locale, String>) : LocaleEnum {
    DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Select the target character.",
            Locale.ITALIAN to "Seleziona il personaggio target."
        )
    ),
    NO_SOURCE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "No source character specified.",
            Locale.ITALIAN to "Nessun personaggio d'origine trovato."
        )
    ),
    INVALID_TARGET_NUMBER(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Invalid number of targets.",
            Locale.ITALIAN to "Numero di personaggi target non valido."
        )
    ),
    TITLE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "has more than one active character.",
            Locale.ITALIAN to "ha più di un personaggio attivo."
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}