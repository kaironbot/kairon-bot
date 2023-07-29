package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class CommandInfoLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    ALLOWED_ROLES(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Allowed roles",
            Locale.ITALIAN to "Ruoli abilitati"
        )
    ),
    DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Describes a command",
            Locale.ITALIAN to "Descrive un comando"
        )
    ),
    EVERYONE_ALLOWED(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "All the users are allowes to run this command",
            Locale.ITALIAN to "Tutti gli utenti sono abilitati a lanciare questo comando"
        )
    ),
    COMMAND(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The command to show",
            Locale.ITALIAN to "Il comando da mostrare"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}