package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class AssignLanguageLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    ALTERNATIVE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Did you mean: ",
            Locale.ITALIAN to "Forse intendevi: "
        )
    ),
    ASSIGN_ALTERNATIVE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Assign ",
            Locale.ITALIAN to "Assegna "
        )
    ),
    LANGUAGE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The language to assign",
            Locale.ITALIAN to "Il linguaggio da assegnare"
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
            Locale.ENGLISH_GREAT_BRITAIN to "The user to give the language to",
            Locale.ITALIAN to "L'utente a cui assegnare il linguaggio"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}