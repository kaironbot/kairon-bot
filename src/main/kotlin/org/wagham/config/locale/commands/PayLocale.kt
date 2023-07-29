package org.wagham.config.locale.commands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class PayLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    AMOUNT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The amount of money to pay",
            Locale.ITALIAN to "La quantità di monete da pagare"
        )
    ),
    ANOTHER_TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Another user to pay",
            Locale.ITALIAN to "Un altro utente da pagare"
        )
    ),
    DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Pay another player",
            Locale.ITALIAN to "Paga un altro giocatore"
        )
    ),
    NOT_ENOUGH_MONEY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "You do not have enough money to complete this operation",
            Locale.ITALIAN to "Non hai abbastanza soldi per completare questa operazione"
        )
    ),
    TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The user to pay",
            Locale.ITALIAN to "L'utente da pagare"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}