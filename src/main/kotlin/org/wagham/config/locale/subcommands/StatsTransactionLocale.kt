package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class StatsTransactionLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    NO_TRANSACTIONS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "There are no transactions for this character",
            Locale.ITALIAN to "Non ci sono transazioni per questo personaggio"
        )
    ),
    TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The character to show the transaction for",
            Locale.ITALIAN to "Il personaggio per cui mostrare le transazioni"
        )
    ),
    TITLE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Last transactions of",
            Locale.ITALIAN to "Ultime transazioni di"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}