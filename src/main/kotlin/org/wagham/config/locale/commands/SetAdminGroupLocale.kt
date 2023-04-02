package org.wagham.config.locale.commands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class SetAdminGroupLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    CURRENT_ROLE(
        mapOf(
            Locale.ITALIAN to "Current admin role is:",
            Locale.ENGLISH_GREAT_BRITAIN to "Il ruolo amministratore è:"
        )
    ),
    ROLE(
        mapOf(
            Locale.ITALIAN to "Il ruolo da settare come amministratore",
            Locale.ENGLISH_GREAT_BRITAIN to "The role to set as admin"
        )
    );

    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}