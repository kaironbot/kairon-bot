package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class BuildingUpgradeLocale(val localeMap: Map<Locale, String>) : LocaleEnum {
    SELECT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Select a building type to start",
            Locale.ITALIAN to "Seleziona un tipo di edificio per cominciare"
        )
    ),
    TITLE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Upgrade a Building:",
            Locale.ITALIAN to "Potenzia un edificio:"
        )
    ),
    TYPE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Type",
            Locale.ITALIAN to "Tipo"
        )
    ),
    UPGRADE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Upgrade",
            Locale.ITALIAN to "Potenzia"
        )
    ),
    UPGRADE_COST(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Upgrade cost",
            Locale.ITALIAN to "Costo del potenziamento"
        )
    ),
    UPGRADE_COST_WITH_PROFICIENCY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Upgrade cost with proficiency in",
            Locale.ITALIAN to "Costo del potenziamento con competenza in"
        )
    ),
    UPGRADE_TO(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Can be upgraded to",
            Locale.ITALIAN to "Può essere potenziato in"
        )
    );

    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}