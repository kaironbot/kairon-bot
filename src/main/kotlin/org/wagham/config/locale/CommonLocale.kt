package org.wagham.config.locale

enum class CommonLocale(private val localeMap: Map<String, String>): LocaleEnum {
    GENERIC_ERROR(
        mapOf(
            "it" to "Qualcosa è andato storto",
            "en" to "Something went wrong"
        )
    ),
    INTERACTION_EXPIRED(
        mapOf(
            "it" to "Interazione scaduta",
            "en" to "Interaction expired"
        )
    ),
    INTERACTION_STARTED_BY_OTHER(
        mapOf(
            "it" to "Non puoi interagire con questo messaggio",
            "en" to "You did not start this interaction"
        )
    ),
    NO_ACTIVE_CHARACTER(
        mapOf(
            "it" to "Non hai nessun personaggio attivo",
            "en" to "You have no active character"
        )
    ),
    NOT_ENOUGH_MONEY(
        mapOf(
            "it" to "Non hai abbastanza monete per completare questa operazione",
            "en" to "You do not have enough money to complete this operation"
        )
    ),
    NOT_ENOUGH_T2BADGE(
        mapOf(
            "it" to "Non hai abbastanza 1DayT2Badge per completare questa operazione",
            "en" to "You do not have enough 1DayT2Badge to complete this operation"
        )
    );

    override fun locale(language: String) = localeMap[language] ?: localeMap["en"]!!
}
