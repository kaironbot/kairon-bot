package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class CharacterUpdateStatusLocale(val localeMap: Map<Locale, String>) : LocaleEnum {
    DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Update the status of a character",
            Locale.ITALIAN to "Aggiorna lo stato di un personaggio"
        )
    ),
    STATUS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The status to set",
            Locale.ITALIAN to "The status to set"
        )
    ),
    TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "A player to update a character for",
            Locale.ITALIAN to "Un utente per cui aggiornare un personaggio"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}