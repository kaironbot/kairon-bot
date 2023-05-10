package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class TakeToolLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    TOOL(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The tool proficiency to take",
            Locale.ITALIAN to "La competenza nello strumento da rimuovere"
        )
    ),
    NOT_FOUND(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Tool proficiency not found: ",
            Locale.ITALIAN to "Competenza non non trovata: "
        )
    ),
    TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The user to remove the tool proficiency from",
            Locale.ITALIAN to "L'utente a cui rimuovere la compentenza"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}