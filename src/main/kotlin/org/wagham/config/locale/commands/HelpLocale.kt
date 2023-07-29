package org.wagham.config.locale.commands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class HelpLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Show info about the commands of this bot",
            Locale.ITALIAN to "Mostra i comandi di questo bot"
        )
    ),
    TITLE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Available Commands",
            Locale.ITALIAN to "Comandi disponibili"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}