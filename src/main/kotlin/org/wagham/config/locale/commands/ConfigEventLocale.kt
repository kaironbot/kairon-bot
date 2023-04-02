package org.wagham.config.locale.commands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class ConfigEventLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    ACTIVE_IN_CHANNELS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Active in the following channels:",
            Locale.ITALIAN to "Attivo nei seguenti canali:"
        )
    ),
    ADD_SUBCOMMAND_CHANNEL(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The channel to add",
            Locale.ITALIAN to "Il canale su cui aggiungere l'evento"
        )
    ),
    ADD_SUBCOMMAND_DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Allows the event to be fired on a channel",
            Locale.ITALIAN to "Permette l'accesso a un canale ad un evento"
        )
    ),
    INFO_SUBCOMMAND_DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Gets the info of an event",
            Locale.ITALIAN to "Visualizza le informazioni riguardo un evento"
        )
    ),
    INFO_SUBCOMMAND_EVENT(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The event to configure",
            Locale.ITALIAN to "L'evento da configurare"
        )
    ),
    REMOVE_SUBCOMMAND_CHANNEL(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The channel to remove",
            Locale.ITALIAN to "Il canale su cui rimuovere l'evento"
        )
    ),
    REMOVE_SUBCOMMAND_DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Deny the access to a channel to an event",
            Locale.ITALIAN to "Rimuove l'accesso a un canale ad un evento"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}