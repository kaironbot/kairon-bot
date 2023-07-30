package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class ItemCraftLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    AMOUNT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The quantity of items to craft",
            Locale.ITALIAN to "La quantità di oggetti da costruire"
        )
    ),
    BUILDINGS_REQUIRED(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Buildings required to craft the item",
            Locale.ITALIAN to "Edifici richiesti per costruire l'oggetto"
        )
    ),
    CANNOT_CRAFT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "This item cannot be crafted.",
            Locale.ITALIAN to "Questo oggetto non può essere costruito."
        )
    ),
    DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Craft an item with the current character",
            Locale.ITALIAN to "Costruisci un oggetto con il personaggio corrente"
        )
    ),
    INVALID_QTY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Invalid quantity provided",
            Locale.ITALIAN to "Quantità non valida"
        )
    ),
    ITEM(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The item to craft",
            Locale.ITALIAN to "L'oggetto da costruire"
        )
    ),
    MISSING_MATERIALS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "You need the following additional materials to craft this item",
            Locale.ITALIAN to "Ti mancano i seguenti materiali per craftre questo oggetto"
        )
    ),
    NOT_ENOUGH(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The minimum craft quantity for this item is",
            Locale.ITALIAN to "La quantità minima costruibile di questo oggetto è"
        )
    ),
    NOT_FOUND(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Item not found: ",
            Locale.ITALIAN to "Oggetto non trovato: "
        )
    ),
    READY_ON(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Item will be ready on",
            Locale.ITALIAN to "L'oggetto sarà pronto"
        )
    ),
    TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The user to give the item to",
            Locale.ITALIAN to "L'utente a cui assegnare l'oggetto"
        )
    ),
    TOOLS_REQUIRED(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Proficiencies required to craft the item",
            Locale.ITALIAN to "Competenze richieste per costruire l'oggetto"
        )
    ),
    TOO_MUCH(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The maximum craft quantity for this item is",
            Locale.ITALIAN to "La quantità massima costruibile di questo oggetto è"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}