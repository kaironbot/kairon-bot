package org.wagham.config.locale.commands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class SetChannelLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    CURRENT_CHANNEL(
        mapOf(
            Locale.ITALIAN to "Il canale CHANNEL_TYPE è:",
            Locale.ENGLISH_GREAT_BRITAIN to "Current CHANNEL_TYPE channel is:"
        )
    ),
    CHANNEL(
        mapOf(
            Locale.ITALIAN to "Il tipo di canale",
            Locale.ENGLISH_GREAT_BRITAIN to "The channel type"
        )
    ),
    CHANNEL_TYPE(
        mapOf(
            Locale.ITALIAN to "Il canale da impostare",
            Locale.ENGLISH_GREAT_BRITAIN to "The channel to set"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}