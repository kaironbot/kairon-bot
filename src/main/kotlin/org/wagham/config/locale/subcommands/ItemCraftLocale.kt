package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class ItemCraftLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    ALTERNATIVE_RECIPES(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "You have multiple ways of crafting this item.",
            Locale.ITALIAN to "Puoi costruire questo oggetto in più modi."
        )
    ),
    ALTERNATIVE_RECIPES_SELECT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Choose a recipe:",
            Locale.ITALIAN to "Scegli una ricetta:"
        )
    ),
    AMOUNT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The quantity of items to craft",
            Locale.ITALIAN to "La quantità di oggetti da costruire"
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
    NOT_CRAFTABLE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "This item cannot be crafted",
            Locale.ITALIAN to "Questo oggetto non può essere craftato"
        )
    ),
    NOT_FOUND(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Item not found: ",
            Locale.ITALIAN to "Oggetto non trovato: "
        )
    ),
    NO_RECIPE_AVAILABLE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "You do not have enough resources to craft this item.",
            Locale.ITALIAN to "Non hai abbastanza risorse per costruire questo oggetto."
        )
    ),
    READY_ON(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Item will be ready on",
            Locale.ITALIAN to "L'oggetto sarà pronto"
        )
    ),
    RECIPE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Recipe",
            Locale.ITALIAN to "Ricetta"
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