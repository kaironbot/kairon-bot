package org.wagham.config.locale.events

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class WeeklyMarketLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    ALREADY_BOUGHT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "You already bought this item.",
            Locale.ITALIAN to "Hai già comprato questo oggetto."
        )
    ),
    BUY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Buy",
            Locale.ITALIAN to "Acquista"
        )
    ),
    DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Let's see what the merchants brought:",
            Locale.ITALIAN to "Vediamo cos'hanno portato i mercanti:"
        )
    ),
    NO_ITEM(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "No item found for this id.",
            Locale.ITALIAN to "Nessun oggetto trovato per questo id."
        )
    ),
    NO_MARKET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "No market found for this guild.",
            Locale.ITALIAN to "Nessun mercato trovato in questo server."
        )
    ),
    TITLE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Enter into the Market",
            Locale.ITALIAN to "Entrate nel mercato"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}