package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class TakeMoneyLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    ALTERNATIVE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Did you mean: ",
            Locale.ITALIAN to "Forse intendevi: "
        )
    ),
    AMOUNT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The amount of money to take",
            Locale.ITALIAN to "La quantità di monete da rimuovere"
        )
    ),
    ANOTHER_TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Another user to take money from",
            Locale.ITALIAN to "Un altro utente a cui togliere monete"
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
            Locale.ENGLISH_GREAT_BRITAIN to "The user to give the item to",
            Locale.ITALIAN to "L'utente a cui assegnare l'oggetto"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}