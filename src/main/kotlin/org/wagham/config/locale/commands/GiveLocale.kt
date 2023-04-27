package org.wagham.config.locale.commands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class GiveLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    ITEM(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The item to give",
            Locale.ITALIAN to "L'oggetto da cedere"
        )
    ),
    NOT_ENOUGH_ITEMS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "You do not have enough items to complete this operation:",
            Locale.ITALIAN to "Non hai abbastanza oggetti per completare questa operazione:"
        )
    ),
    QUANTITY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The number of items to give",
            Locale.ITALIAN to "La quantità di oggetti da cedere"
        )
    ),
    TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The user to give the item(s) to",
            Locale.ITALIAN to "L'utente a cui cedere l'oggetto / gli oggetti"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}