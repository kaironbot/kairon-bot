package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class ItemSellLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    AMOUNT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The quantity of items to sell",
            Locale.ITALIAN to "La quantità di oggetti da vendere"
        )
    ),
    CANNOT_SELL(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "This item cannot be sold.",
            Locale.ITALIAN to "Questo oggetto non può essere venduto."
        )
    ),
    DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Sell an item with the current character",
            Locale.ITALIAN to "Vendi un oggetto con il personaggio corrente"
        )
    ),
    ITEM(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The item to sell",
            Locale.ITALIAN to "L'oggetto da vendere"
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