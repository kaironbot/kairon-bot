package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class AssignItemsLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Assign multiple items to a player",
            Locale.ITALIAN to "Assegna più oggetti a un giocatore"
        )
    ),
    ITEMS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The items to give",
            Locale.ITALIAN to "Gli oggetti da assegnare"
        )
    ),
    NOT_FOUND(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Items not found: ",
            Locale.ITALIAN to "Oggetti non trovato: "
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