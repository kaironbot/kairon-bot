package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class ItemBuyLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    AMOUNT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The quantity of items to buy",
            Locale.ITALIAN to "La quantità di oggetti da acquistare"
        )
    ),
    CANNOT_BUY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "This item cannot be bought.",
            Locale.ITALIAN to "Questo oggetto non può essere acquistato."
        )
    ),
    ITEM(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The item to buy",
            Locale.ITALIAN to "L'oggetto da acquistare"
        )
    ),
    NOT_FOUND(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Item not found: ",
            Locale.ITALIAN to "Oggetto non trovato: "
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