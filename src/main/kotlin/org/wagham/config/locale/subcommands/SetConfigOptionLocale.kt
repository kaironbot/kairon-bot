package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class SetConfigOptionLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    CONFIG_TYPE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The configuration to set",
            Locale.ITALIAN to "La configurazione da impostare"
        )
    ),
    VALUE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The value to set",
            Locale.ITALIAN to "Il valore da impostare"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}