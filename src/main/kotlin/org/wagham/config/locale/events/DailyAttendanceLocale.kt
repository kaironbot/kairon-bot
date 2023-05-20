package org.wagham.config.locale.events

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class DailyAttendanceLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    CANNOT_REGISTER(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Cannot register to the daily availability.",
            Locale.ITALIAN to "Impossibile registrarsi alla disponibilità giornaliera."
        )
    ),
    DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Use the buttons to express your availability to play.",
            Locale.ITALIAN to "Usa i pulsanti per registrare la tua disponibilità a giocare."
        )
    ),
    DEREGISTER_AFTERNOON(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Deregister for the afternoon",
            Locale.ITALIAN to "Annulla registrazione per il pomeriggio"
        )
    ),
    DEREGISTER_EVENING(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Deregister for the evening",
            Locale.ITALIAN to "Annulla registrazione per la sera"
        )
    ),
    AFTERNOON_AVAILABILITY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Players available this afternoon:",
            Locale.ITALIAN to "Giocatori disponibili questo pomeriggio:"
        )
    ),
    EVENING_AVAILABILITY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Players available this evening:",
            Locale.ITALIAN to "Giocatori disponibili stasera:"
        )
    ),
    LAST_ACTIVITY(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The number in parentheses represents the number of days since last played.",
            Locale.ITALIAN to "Il numero tra parentesi rappresenta il numero di giorni dall'ultima giocata."
        )
    ),
    REGISTER_AFTERNOON(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Register for the afternoon",
            Locale.ITALIAN to "Registrati per il pomeriggio"
        )
    ),
    REGISTER_EVENING(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Register for the evening",
            Locale.ITALIAN to "Registrati per la sera"
        )
    ),
    TITLE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Daily Attendance",
            Locale.ITALIAN to "Disponibilità Odierna"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}