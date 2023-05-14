package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class SetRemoveBuildingLimitLocale(val localeMap: Map<Locale, String>): LocaleEnum {
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