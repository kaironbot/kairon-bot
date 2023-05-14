package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class BuildingInfoLocale(val localeMap: Map<Locale, String>) : LocaleEnum {
    BUILDING(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The building to search",
            Locale.ITALIAN to "L'edificio da cercare"
        )
    ),
    BUILDING_NOT_FOUND(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Building not found",
            Locale.ITALIAN to "Edificio non trovato"
        )
    ),
    BUILDING_NAME(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Building name",
            Locale.ITALIAN to "Nome dell'edificio"
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
    );

    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}