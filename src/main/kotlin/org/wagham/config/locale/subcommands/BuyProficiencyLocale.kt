package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class BuyProficiencyLocale(val localeMap: Map<Locale, String>) : LocaleEnum {
    NO_PROFICIENCY_SELECTED(
        mapOf(
            Locale.ITALIAN to "Nessuna competenza selezionata",
            Locale.ENGLISH_GREAT_BRITAIN to "No proficiency selected"
        )
    ),
    ALREADY_HAS_PROFICIENCY(
        mapOf(
            Locale.ITALIAN to "Hai già questa competenza: ",
            Locale.ENGLISH_GREAT_BRITAIN to "You already have this proficiency: "
        )
    ),
    BUY_PROFICIENCY_SUCCESS(
        mapOf(
            Locale.ITALIAN to "Operazione completata con successo, competenza acquistata: ",
            Locale.ENGLISH_GREAT_BRITAIN to "Operation completed successfully, proficiency bought: "
        )
    ),
    YOU_SEARCHED(
        mapOf(
            Locale.ITALIAN to "You searched:",
            Locale.ENGLISH_GREAT_BRITAIN to "Hai cercato:"
        )
    ),
    POSSIBLE_OPTIONS(
        mapOf(
            Locale.ITALIAN to "Possible options:",
            Locale.ENGLISH_GREAT_BRITAIN to "Possibili opzioni:"
        )
    ),
    SEARCH_PARAMETER(
        mapOf(
            Locale.ITALIAN to "La competenza da cercare",
            Locale.ENGLISH_GREAT_BRITAIN to "A proficiency to search"
        )
    );

    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}