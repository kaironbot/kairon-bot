package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class TakeItemLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    AMOUNT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The quantity of items to take",
            Locale.ITALIAN to "La quantità di oggetti da rimuovere"
        )
    ),
    DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Take an item from a player",
            Locale.ITALIAN to "Togli un oggetto a un giocatore"
        )
    ),
    ITEM(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The item to take",
            Locale.ITALIAN to "L'oggetto da take"
        )
    ),
    NOT_FOUND(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Player has not enough items: ",
            Locale.ITALIAN to "Il giocatore non ha abbastanza elementi di questo oggetto: "
        )
    ),
    TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The user to take the item from",
            Locale.ITALIAN to "L'utente a cui togliere l'oggetto"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}