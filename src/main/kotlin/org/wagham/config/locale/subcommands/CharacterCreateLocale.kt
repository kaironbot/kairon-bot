package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class CharacterCreateLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    ABORTED(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Operation aborted",
            Locale.ITALIAN to "Operazione annullata"
        )
    ),
    AGE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Age (optional)",
            Locale.ITALIAN to "Età (opzionale)"
        )
    ),
    CHARACTER_CLASS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Starting character class",
            Locale.ITALIAN to "Classe di partenza"
        )
    ),
    CHARACTER_FOR(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Creating character for:",
            Locale.ITALIAN to "Creazione del personaggio per:"
        )
    ),
    CHARACTER_NAME(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Character name",
            Locale.ITALIAN to "Nome del personaggio"
        )
    ),
    CHARACTER_NAME_INVALID(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The provided character name is invalid",
            Locale.ITALIAN to "Il nome del personaggio non è valido"
        )
    ),
    CHARACTER_LEVEL(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Starting level",
            Locale.ITALIAN to "Livello di partenza"
        )
    ),
    CHARACTER_LEVEL_INVALID(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The provided starting level is not valid",
            Locale.ITALIAN to "Il livello di partenza non è valido"
        )
    ),
    CHARACTER_RACE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Race",
            Locale.ITALIAN to "Razza"
        )
    ),
    INSERT_MISSING(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Choose an option for the character:",
            Locale.ITALIAN to "Scegli un'opzione per il personaggio:"
        )
    ),
    INVALID_CHARACTER_DATA(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Invalid character data",
            Locale.ITALIAN to "Dati di creazione non validi"
        )
    ),
    MISSING_CLASS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Character class is missing",
            Locale.ITALIAN to "La classe del personaggio non è stata inserita"
        )
    ),
    MISSING_RACE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Character race is missing",
            Locale.ITALIAN to "La razza del personaggio non è stata inserita"
        )
    ),
    MODAL_TITLE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Create a new character",
            Locale.ITALIAN to "Crea un nuovo personaggio"
        )
    ),
    ORIGIN(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Place of origin (optional)",
            Locale.ITALIAN to "Territorio d'origine (opzionale)"
        )
    ),
    PLAYER(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The player to create the character for",
            Locale.ITALIAN to "Il giocatore per cui creare il personaggio"
        )
    ),
    PROCESS_EXPIRED(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "The creation process expired, try again.",
            Locale.ITALIAN to "Il processo di creazione è scaduto, riprova."
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}