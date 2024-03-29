package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class ToolBuyLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    ALREADY_POSSESS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "This character already knows this tool proficiency.",
            Locale.ITALIAN to "Questo personaggio conosce già questa competenza."
        )
    ),
    CANNOT_BUY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "This proficiency cannot be bought.",
            Locale.ITALIAN to "Questa competenza non può essere acquistata."
        )
    ),
    DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Buy a tool proficiency with the current character",
            Locale.ITALIAN to "Compra la competenza in uno strumento con il personaggio corrente"
        )
    ),
    LEARNING(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "You are learning this proficiency. It will be finished on:",
            Locale.ITALIAN to "Stai già imparando questa compentenza. Sarà disponibile il:"
        )
    ),
    MISSING_MATERIALS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "You need the following additional materials to buy this tool proficiency",
            Locale.ITALIAN to "Ti mancano i seguenti materiali per acquistare questa competenza"
        )
    ),
    NOT_FOUND(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Tool proficiency not found: ",
            Locale.ITALIAN to "Competenza non trovata: "
        )
    ),
    PROFICIENCY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The proficiency to buy",
            Locale.ITALIAN to "La competenza da acquistare"
        )
    ),
    READY_ON(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "You will get the proficiency on",
            Locale.ITALIAN to "Otterrai la competenza il"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}