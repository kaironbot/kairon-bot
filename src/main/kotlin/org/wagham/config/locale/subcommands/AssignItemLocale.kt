package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class AssignItemLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    AMOUNT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The quantity of items to give",
            Locale.ITALIAN to "La quantità di oggetti da assegnare"
        )
    ),
    ANOTHER_TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Another user to give the item to",
            Locale.ITALIAN to "Un altro utente a cui assegnare l'oggetto"
        )
    ),
    ITEM(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The item to give",
            Locale.ITALIAN to "L'oggetto da assegnare"
        )
    ),
    TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The user to give the item to",
            Locale.ITALIAN to "L'utente a cui assegnare l'oggetto"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}