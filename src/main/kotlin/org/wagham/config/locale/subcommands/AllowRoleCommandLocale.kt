package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class AllowRoleCommandLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    COMMAND(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The command to configure",
            Locale.ITALIAN to "Il comando da configurare"
        )
    ),
    ROLE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The role to allow",
            Locale.ITALIAN to "Il ruolo da abilitare"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}