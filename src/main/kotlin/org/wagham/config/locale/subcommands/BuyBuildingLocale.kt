package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class BuyBuildingLocale(val localeMap: Map<Locale, String>) : LocaleEnum {
    COMMAND_DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Buy a building with the current character",
            Locale.ITALIAN to "Acquista un edificio con il tuo personaggio"
        )
    ),
    BUILDING_AREA(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Construction area",
            Locale.ITALIAN to "Zona di costruzione"
        )
    ),
    BUILD(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Buy",
            Locale.ITALIAN to "Acquista"
        )
    ),
    BUILDING_COST(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Building cost",
            Locale.ITALIAN to "Costo dell'edificio"
        )
    ),
    BUILDING_COST_WITH_PROFICIENCY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Building cost with proficiency in:",
            Locale.ITALIAN to "Costo dell'edificio con competenza in:"
        )
    ),
    BUILDING_DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Building description",
            Locale.ITALIAN to "Descrizione dell'edificio"
        )
    ),
    BUILDING_NAME(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Building name",
            Locale.ITALIAN to "Nome dell'edificio"
        )
    ),
    BUILDING_SIZE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Size",
            Locale.ITALIAN to "Dimensione"
        )
    ),
    SELECT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Select a building type to start",
            Locale.ITALIAN to "Seleziona un tipo di edificio per cominciare"
        )
    ),
    TITLE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Buy a Building:",
            Locale.ITALIAN to "Acquista un edificio:"
        )
    ),
    WEEKLY_PRIZE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Weekly prizes:",
            Locale.ITALIAN to "Premi settimanali:"
        )
    );

    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}