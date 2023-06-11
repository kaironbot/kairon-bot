package org.wagham.config.locale.commands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class InventoryLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    ITEMS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Items",
            Locale.ITALIAN to "Oggetti"
        )
    ),
    LABEL_NEXT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Next",
            Locale.ITALIAN to "Successivo"
        )
    ),
    LABEL_PREVIOUS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Previous",
            Locale.ITALIAN to "Precedente"
        )
    ),
    MONEY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Money",
            Locale.ITALIAN to "Monete"
        )
    ),
    TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "A user to show the inventory for the active character",
            Locale.ITALIAN to "Un utente per cui mostrare l'inventario"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}