package org.wagham.config.locale.commands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class ConvertTBadgeLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    AMOUNT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Amount of the source type tBadge to convert",
            Locale.ITALIAN to "Quantità di tBadge del tipo di origine da convertire"
        )
    ),
    DESTINATION_TYPE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Destination tBadge type",
            Locale.ITALIAN to "Tipo tBadge di destinazione"
        )
    ),
    NOT_ENOUGH_TBADGE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Not enough",
            Locale.ITALIAN to "Non hai abbastanza"
        )
    ),
    SOURCE_TYPE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Source tBadge type",
            Locale.ITALIAN to "Tipo tBadge di origine"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}