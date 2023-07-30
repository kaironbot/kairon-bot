package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class SetRemoveBuildingLimitLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Remove a limit on the maximum number of buildings a player can have",
            Locale.ITALIAN to "Rimuove un limite sul massimo numero di edifici che un giocatore può avere"
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