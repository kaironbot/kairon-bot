package org.wagham.config.locale.commands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class RollLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Roll one or more dice and add modifiers",
            Locale.ITALIAN to "Tira uno o più dadi e aggiungi modificatori"
        )
    ),
    FORMULA(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The die to roll (eg. 1d4+1d20-3)",
            Locale.ITALIAN to "I dadi da tirare (es. 1d4+1d20-3)"
        )
    ),
    YOU_ROLLED(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "You rolled",
            Locale.ITALIAN to "Hai fatto"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}