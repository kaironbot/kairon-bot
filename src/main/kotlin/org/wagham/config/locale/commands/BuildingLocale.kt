package org.wagham.config.locale.commands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class BuildingLocale(val localeMap: Map<Locale, String>) : LocaleEnum {
    BY_UPGRADE_ONLY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to ":tools: Can only be obtained by upgrading an existing building.",
            Locale.ITALIAN to ":tools: Può essere ottenuto solo potenziando un edificio esistente."
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
    DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Buy, upgrade and get info about buildings.",
            Locale.ITALIAN to "Compra, potenzia e ottieni informazioni sugli edifici. "
        )
    ),
    TYPE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Type",
            Locale.ITALIAN to "Tipo"
        )
    ),
    TYPE_LIMIT_REACHED(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to ":warning: You have the maximum number of buildings of this type. Server limit: ",
            Locale.ITALIAN to ":warning: Attenzione, hai raggiunto il massimo numero di edifici per questo tipo. Limite del server: "
        )
    ),
    UPGRADABLE_IN(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Can be upgraded to",
            Locale.ITALIAN to "Può essere potenziato e diventare"
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