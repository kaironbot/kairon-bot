package org.wagham.config.locale

import dev.kord.common.Locale

enum class CommonLocale(private val localeMap: Map<Locale, String>): LocaleEnum {
    ABORT(
        mapOf(
            Locale.ITALIAN to "Annulla",
            Locale.ENGLISH_GREAT_BRITAIN to "Abort"
        )
    ),
    ABORTED(
        mapOf(
            Locale.ITALIAN to "Operazione annullata",
            Locale.ENGLISH_GREAT_BRITAIN to "Operation canceled succesfully"
        )
    ),
    ALTERNATIVE(
        mapOf(
            Locale.ITALIAN to "Forse intendevi: ",
            Locale.ENGLISH_GREAT_BRITAIN to "Did you mean: "
        )
    ),
    ASSIGN_ALTERNATIVE(
        mapOf(
            Locale.ITALIAN to "Assegna ",
            Locale.ENGLISH_GREAT_BRITAIN to "Assegna "
        )
    ),
    CONFIRM(
        mapOf(
            Locale.ITALIAN to "Conferma",
            Locale.ENGLISH_GREAT_BRITAIN to "Confirm"
        )
    ),
    CONTINUE(
        mapOf(
            Locale.ITALIAN to "Continua",
            Locale.ENGLISH_GREAT_BRITAIN to "Continue"
        )
    ),
    ELEMENT_NOT_FOUND(
        mapOf(
            Locale.ITALIAN to "Elemento non trovato: ",
            Locale.ENGLISH_GREAT_BRITAIN to "Element not found: "
        )
    ),
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
    MULTIPLE_CHARACTERS(
        mapOf(
            Locale.ITALIAN to "Hai più di un personaggio attivo, imposta il personaggio corrente col comando `/as character`",
            Locale.ENGLISH_GREAT_BRITAIN to "You have more than one active character. Set your current character with the `/as character` command"
        )
    ),
    NO(
        mapOf(
            Locale.ITALIAN to "No",
            Locale.ENGLISH_GREAT_BRITAIN to "No"
        )
    ),
    NO_ACTIVE_CHARACTER(
        mapOf(
            Locale.ITALIAN to "non ha nessun personaggio attivo",
            Locale.ENGLISH_GREAT_BRITAIN to "has no active character"
        )
    ),
    NOT_ENOUGH_ITEMS(
        mapOf(
            Locale.ITALIAN to "Non hai abbastanza oggetti per completare questa operazione: ",
            Locale.ENGLISH_GREAT_BRITAIN to "You do not have enough item to complete this operation: "
        )
    ),
    NOT_ENOUGH_MONEY(
        mapOf(
            Locale.ITALIAN to "Non hai abbastanza monete per completare questa operazione",
            Locale.ENGLISH_GREAT_BRITAIN to "You do not have enough money to complete this operation"
        )
    ),
    SUCCESS(
        mapOf(
            Locale.ITALIAN to "Operazione completata con successo",
            Locale.ENGLISH_GREAT_BRITAIN to "Operation completed successfully"
        )
    ),
    YES(
        mapOf(
            Locale.ITALIAN to "Sì",
            Locale.ENGLISH_GREAT_BRITAIN to "Yes"
        )
    ),
    UNAUTHORIZED(
        mapOf(
            Locale.ITALIAN to "Solo i seguenti ruoli possono accedere a questo comando: ",
            Locale.ENGLISH_GREAT_BRITAIN to "Only the following roles can access this command: "
        )
    ),
    UNKNOWN_OP(
        mapOf(
            Locale.ITALIAN to "Operazione sconosciuta",
            Locale.ENGLISH_GREAT_BRITAIN to "Unknown operation"
        )
    );

    override fun locale(language: String) = locale(Locale.fromString(language))
    override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}
