package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class ItemInfoLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    ATTUNEMENT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Attunement required?",
            Locale.ITALIAN to "Richiede sintonia?"
        )
    ),
    BUYABLE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Can be bought?",
            Locale.ITALIAN to "Acquistabile?"
        )
    ),
    ITEM(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The item to show",
            Locale.ITALIAN to "L'oggetto da mostrare"
        )
    ),
    SELLABLE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Can be sold?",
            Locale.ITALIAN to "Vendibile?"
        )
    ),
    TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The user to give the item to",
            Locale.ITALIAN to "L'utente a cui assegnare l'oggetto"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}