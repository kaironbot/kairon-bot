package org.wagham.config.locale

import dev.kord.common.Locale

enum class CommonLocale(private val localeMap: Map<Locale, String>): LocaleEnum {
    ERROR(
        mapOf(
            Locale.ITALIAN to "Error",
            Locale.ENGLISH_GREAT_BRITAIN to "Errore"
        )
    ),
    GENERIC_ERROR(
        mapOf(
            Locale.ITALIAN to "Qualcosa è andato storto",
            Locale.ENGLISH_GREAT_BRITAIN to "Something went wrong"
        )
    ),
    INTERACTION_EXPIRED(
        mapOf(
            Locale.ITALIAN to "Interazione scaduta",
            Locale.ENGLISH_GREAT_BRITAIN to "Interaction expired"
        )
    ),
    INTERACTION_STARTED_BY_OTHER(
        mapOf(
            Locale.ITALIAN to "Non puoi interagire con questo messaggio",
            Locale.ENGLISH_GREAT_BRITAIN to "You did not start this interaction"
        )
    ),
    NO_ACTIVE_CHARACTER(
        mapOf(
            Locale.ITALIAN to "Non hai nessun personaggio attivo",
            Locale.ENGLISH_GREAT_BRITAIN to "You have no active character"
        )
    ),
    NOT_ENOUGH_MONEY(
        mapOf(
            Locale.ITALIAN to "Non hai abbastanza monete per completare questa operazione",
            Locale.ENGLISH_GREAT_BRITAIN to "You do not have enough money to complete this operation"
        )
    ),
    NOT_ENOUGH_T2BADGE(
        mapOf(
            Locale.ITALIAN to "Non hai abbastanza 1DayT2Badge per completare questa operazione",
            Locale.ENGLISH_GREAT_BRITAIN to "You do not have enough 1DayT2Badge to complete this operation"
        )
    ),
    SUCCESS(
        mapOf(
            Locale.ITALIAN to "Operazione completata con successo",
            Locale.ENGLISH_GREAT_BRITAIN to "Operation completed successfully"
        )
    );

    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}
