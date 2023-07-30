package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class DenyRoleLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    COMMAND(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The command to configure",
            Locale.ITALIAN to "Il comando da configurare"
        )
    ),
    DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Remove a role to the ones that can use this command",
            Locale.ITALIAN to "Rimuove un ruolo dalla lista di quelli che possono usare questo comando"
        )
    ),
    ROLE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The role to deny",
            Locale.ITALIAN to "Il ruolo da rimuovere"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}