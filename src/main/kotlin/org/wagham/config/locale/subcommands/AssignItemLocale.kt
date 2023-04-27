package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class AssignItemLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    ALTERNATIVE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Did you mean: ",
            Locale.ITALIAN to "Forse intendevi: "
        )
    ),
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
    ASSIGN_ALTERNATIVE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Assign ",
            Locale.ITALIAN to "Assegna "
        )
    ),
    ITEM(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The item to give",
            Locale.ITALIAN to "L'oggetto da assegnare"
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