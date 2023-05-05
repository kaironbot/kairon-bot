package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class AssignLanguageLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    LANGUAGE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The language to assign",
            Locale.ITALIAN to "Il linguaggio da assegnare"
        )
    ),
    TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The user to give the language to",
            Locale.ITALIAN to "L'utente a cui assegnare il linguaggio"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}