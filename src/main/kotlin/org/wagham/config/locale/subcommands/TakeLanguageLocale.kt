package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class TakeLanguageLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    LANGUAGE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The language to take",
            Locale.ITALIAN to "Il linguaggio da rimuovere"
        )
    ),
    NOT_FOUND(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Language not found: ",
            Locale.ITALIAN to "Linguaggio non trovato: "
        )
    ),
    TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The user to remove the language from",
            Locale.ITALIAN to "L'utente a cui rimuovere il linguaggio"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}