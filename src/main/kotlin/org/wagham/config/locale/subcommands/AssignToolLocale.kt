package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class AssignToolLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    TOOL(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The tool proficiency to assign",
            Locale.ITALIAN to "La competenza nello strumento da assegnare"
        )
    ),
    TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The user to give the tool proficiency to",
            Locale.ITALIAN to "L'utente a cui assegnare la competenza"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}