package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class AsCharacterLocale(val localeMap: Map<Locale, String>) : LocaleEnum {
    CHOOSE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Choose your active character.",
            Locale.ITALIAN to "Seleziona il tuo personaggio attivo."
        )
    ),
    CURRENT_SELECTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Current selection:",
            Locale.ITALIAN to "Selezionato:"
        )
    ),
    DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Select your current character for the next operations",
            Locale.ITALIAN to "Seleziona il tuo personaggio attivo per le operazioni successive"
        )
    ),
    ONE_CHARACTER(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "You only have one active character, but whatever.",
            Locale.ITALIAN to "Hai solo un personaggio attivo, quindi non cambia nulla."
        )
    ),
    TITLE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Your current active character is:",
            Locale.ITALIAN to "Il tuo personaggio attivo selezionato è:"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}