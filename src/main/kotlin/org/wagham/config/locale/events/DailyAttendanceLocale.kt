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
    DEREGISTER(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Deregister",
            Locale.ITALIAN to "Annulla registrazione"
        )
    ),
    REGISTER(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Register",
            Locale.ITALIAN to "Registrati"
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