package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class StatsMasterLocale(val localeMap: Map<Locale, String>) : LocaleEnum {
    DATE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Date",
            Locale.ITALIAN to "Data"
        )
    ),
    LABEL_EXPORT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Export",
            Locale.ITALIAN to "Esporta"
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
    MASTER_PARAMETER(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The master to show the stats for",
            Locale.ITALIAN to "Il master per cui mostrare le statistiche"
        )
    ),
    NAVIGATE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Use the buttons to navigate or export the list",
            Locale.ITALIAN to "Usa i pulsanti per navigare o per esportare la lista"
        )
    ),
    NEVER_MASTERED_TITLE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "This player has never mastered: ",
            Locale.ITALIAN to "Questo giocatore non ha mai masterato: "
        )
    ),
    NEVER_MASTERED_DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Start mastering now!",
            Locale.ITALIAN to "Comincia a masterare ora!"
        )
    ),
    REPORT_TO_MP(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The report of the sessions will be sent to you in DM",
            Locale.ITALIAN to "Il report delle sessioni ti verrà inviato tramite messaggio privato"
        )
    ),
    SESSION_LIST(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Session list:",
            Locale.ITALIAN to "Elenco delle sessioni:"
        )
    ),
    SESSION_MASTERED_TITLE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Sessions mastered by ",
            Locale.ITALIAN to "Sessioni masterate da "
        )
    ),
    TITLE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Title",
            Locale.ITALIAN to "Titolo"
        )
    );

    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}