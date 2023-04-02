package org.wagham.config.locale.commands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class PingLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    COMMANDS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Commands",
            Locale.ITALIAN to "Comandi"
        )
    ),
    EVENTS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Events",
            Locale.ITALIAN to "Eventi"
        )
    ),
    ONLINE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "WaghamBot is online",
            Locale.ITALIAN to "WaghamBot è online"
        )
    ),
    NO_COMMAND_FOUND(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "No commands found in this guild",
            Locale.ITALIAN to "Nessun comando trovato in questo server"
        )
    ),
    NO_EVENT_FOUND(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "No events found in this guild",
            Locale.ITALIAN to "Nessun evento trovato in questo server"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}