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
    CAN_BUY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Buy price",
            Locale.ITALIAN to "Prezzo di acquisto"
        )
    ),
    CAN_CRAFT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Can be crafted",
            Locale.ITALIAN to "Craftabile"
        )
    ),
    CAN_GIVE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Give/receive ratio",
            Locale.ITALIAN to "Rapporto oggetti dati:ricevuti"
        )
    ),
    CAN_USE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Usable?",
            Locale.ITALIAN to "Usabile?"
        )
    ),
    CANNOT_BUY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Cannot be bought",
            Locale.ITALIAN to "Non acquistabile"
        )
    ),
    CANNOT_CRAFT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Cannot be crafted",
            Locale.ITALIAN to "Non craftabile"
        )
    ),
    CANNOT_GIVE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Cannot be given",
            Locale.ITALIAN to "Non può essere ceduto"
        )
    ),
    CANNOT_SELL(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Cannot be sold",
            Locale.ITALIAN to "Non vendibile"
        )
    ),
    DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Show the info about an item",
            Locale.ITALIAN to "Mostra le informazioni riguardanti un oggetto"
        )
    ),
    ITEM(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The item to show",
            Locale.ITALIAN to "L'oggetto da mostrare"
        )
    ),
    MATERIAL_OF(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Can be used to craft",
            Locale.ITALIAN to "Può essere usato per craftare"
        )
    ),
    SELLABLE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Sell price",
            Locale.ITALIAN to "Prezzo di vendita"
        )
    ),
    SOURCE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Source",
            Locale.ITALIAN to "Fonte"
        )
    ),
    TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The user to give the item to",
            Locale.ITALIAN to "L'utente a cui assegnare l'oggetto"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap.getValue(Locale.ENGLISH_GREAT_BRITAIN)
}