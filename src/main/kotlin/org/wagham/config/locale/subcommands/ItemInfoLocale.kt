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
    BUILDINGS_REQUIRED(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Buildings required",
            Locale.ITALIAN to "Edifici richiesti"
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
    COST(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Cost",
            Locale.ITALIAN to "Costo"
        )
    ),
    INSTANTANEOUS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Istantaneous",
            Locale.ITALIAN to "Istantaneo"
        )
    ),
    ITEM(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The item to show",
            Locale.ITALIAN to "L'oggetto da mostrare"
        )
    ),
    MATERIALS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Required materials",
            Locale.ITALIAN to "Materiali richiesti"
        )
    ),
    MAX_QTY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Maximum craftable quantity",
            Locale.ITALIAN to "Quantità massima craftabile"
        )
    ),
    MIN_QTY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Minimum craftable quantity",
            Locale.ITALIAN to "Quantità minima craftabile"
        )
    ),
    NO_BUILDINGS_REQUIRED(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "No buildings required",
            Locale.ITALIAN to "Nessun edificio richiesto"
        )
    ),
    NO_MATERIALS_REQUIRED(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "No materials required",
            Locale.ITALIAN to "Nessun materiale richiesto"
        )
    ),
    NO_PROFICIENCIES_REQUIRED(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "No proficiencies required",
            Locale.ITALIAN to "Nessuna competenza richiesta"
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
    ),
    TIME_REQUIRED(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Time required",
            Locale.ITALIAN to "Tempo necessario"
        )
    ),
    TOOL_PROFICIENCIES(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Tool proficiencies required",
            Locale.ITALIAN to "Competenze negli strumenti richieste"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}