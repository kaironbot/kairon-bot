package org.wagham.config.locale.commands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class MeLocale(val localeMap: Map<Locale, String>): LocaleEnum {
    ADD_MULTICLASS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Add a class",
            Locale.ITALIAN to "Aggiungi una classe"
        )
    ),
    REMOVE_MULTICLASS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Remove a class",
            Locale.ITALIAN to "Rimuovi una classe"
        )
    ),
    BUILDINGS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Buildings",
            Locale.ITALIAN to "Edifici"
        )
    ),
    BUILDINGS_DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Buildings owned by this character",
            Locale.ITALIAN to "Edifici di questo personaggio"
        )
    ),
    CLASS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Class",
            Locale.ITALIAN to "Classe"
        )
    ),
    DESCRIPTION(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Show information about your character",
            Locale.ITALIAN to "Mostra informazioni sul tuo personaggio"
        )
    ),
    LANGUAGES(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Languages",
            Locale.ITALIAN to "Linguaggi"
        )
    ),
    LEVEL(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Level",
            Locale.ITALIAN to "Livello"
        )
    ),
    NO_CHARACTER(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "No character found.",
            Locale.ITALIAN to "Nessun personaggio trovato."
        )
    ),
    NO_CLASS_DEFINED(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "No class defined in the server.",
            Locale.ITALIAN to "Nessuna opzione per la classe definita nel server."
        )
    ),
    NO_CLASS_SELECTED(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "No class selected.",
            Locale.ITALIAN to "Nessuna classe selezionata."
        )
    ),
    NO_LANGUAGES(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "This character does not speak any language.",
            Locale.ITALIAN to "Questo personaggio non parla alcun linguaggio."
        )
    ),
    NO_TOOLS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "This character has no proficiency in any tool.",
            Locale.ITALIAN to "Questo personaggio non ha competenza in alcuno strumento."
        )
    ),
    NOT_THE_OWNER(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Only the owner of a character can update their classes.",
            Locale.ITALIAN to "Solo il proprietario di un personaggio ha il permesso di aggiornare le sue classi."
        )
    ),
    ONLY_ONE_CLASS_ERROR(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "You cannot remove your only class.",
            Locale.ITALIAN to "Non puoi rimuovere la tua unica classe."
        )
    ),
    ORIGIN(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Origin",
            Locale.ITALIAN to "Provenienza"
        )
    ),
    RACE(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Race",
            Locale.ITALIAN to "Razza"
        )
    ),
    TARGET(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "A user to show the info for the active character",
            Locale.ITALIAN to "Un utente per cui mostrare le informazioni"
        )
    ),
    TOOLS(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Tool proficiencies",
            Locale.ITALIAN to "Competenze negli strumenti"
        )
    ),
    UPDATE_CHARACTER(
        mapOf(
            Locale.ENGLISH_GREAT_BRITAIN to "Update character",
            Locale.ITALIAN to "Aggiorna il personaggio"
        )
    );
    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}