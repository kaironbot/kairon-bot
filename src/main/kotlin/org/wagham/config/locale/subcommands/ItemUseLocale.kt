package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class ItemUseLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    AMOUNT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The quantity of items to use",
            Locale.ITALIAN to "La quantità di oggetti da usare"
        )
    ),
    CANNOT_USE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "This item cannot be used.",
            Locale.ITALIAN to "Questo oggetto non può essere usato."
        )
    ),
    ITEM(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The item to use",
            Locale.ITALIAN to "L'oggetto da usare"
        )
    ),
    NOT_FOUND(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Item not found",
            Locale.ITALIAN to "Oggetto non trovato"
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