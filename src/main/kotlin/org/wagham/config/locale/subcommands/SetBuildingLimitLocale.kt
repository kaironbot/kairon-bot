package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class SetBuildingLimitLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Set a limit on the maximum number of buildings a player can have",
            Locale.ITALIAN to "Imposta il numero massimo di edifici che un giocatore può avere"
        )
    ),
    INVALID_VALUE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Value not allowed",
            Locale.ITALIAN to "Value not allowed"
        )
    ),
    LIMIT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The maximum number of buildings",
            Locale.ITALIAN to "Il numero massimo di edifici"
        )
    ),
    LIMIT_TYPE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The type of limit",
            Locale.ITALIAN to "The type of limit"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}