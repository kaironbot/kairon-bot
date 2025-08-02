package org.wagham.config.locale.subcommands

import dev.kord.common.Locale
import org.wagham.config.locale.LocaleEnum

enum class NoShowStrikeLocale(val localeMap: Map<Locale, String>): LocaleEnum {
	BANNED_UNTIL(
		mapOf(
			Locale.ENGLISH_GREAT_BRITAIN to "Since you have more than 3 active strikes you cannot register for sessions until: ",
			Locale.ITALIAN to "Dato che hai più di 3 strike attivi, non potrai registrarti alle sessioni fino al: "
		)
	),
	CURRENT_STRIKES(
		mapOf(
			Locale.ENGLISH_GREAT_BRITAIN to "Your currently active strikes are:",
			Locale.ITALIAN to "I tuoi strike attuali sono:"
		)
	),
	DESCRIPTION(
		mapOf(
			Locale.ENGLISH_GREAT_BRITAIN to "Give a strike to a player for a no show",
			Locale.ITALIAN to "Assegna uno strike a un giocatore che non si è presentato"
		)
	),
	SESSION(
		mapOf(
			Locale.ENGLISH_GREAT_BRITAIN to "The title of the session",
			Locale.ITALIAN to "Il titolo della sessione"
		)
	),
	STRIKE_EXPIRATION(
		mapOf(
			Locale.ENGLISH_GREAT_BRITAIN to "Each strike expires after 60 days.",
			Locale.ITALIAN to "Ogni strike dopo 60 giorni."
		)
	),
	STRIKE_RECEIVED(
		mapOf(
			Locale.ENGLISH_GREAT_BRITAIN to "You just received a strike for not showing without warning at the session: ",
			Locale.ITALIAN to "Hai appena ricevuto uno stike per non aver avvertito della tua assenza alla sessione: "
		)
	),
	SUCCESS(
		mapOf(
			Locale.ENGLISH_GREAT_BRITAIN to "Strike added successfully",
			Locale.ITALIAN to "Strike aggiunto con successo"
		)
	),
	TARGET(
		mapOf(
			Locale.ENGLISH_GREAT_BRITAIN to "The player to give the strike to",
			Locale.ITALIAN to "Il giocatore a cui assegnare lo strike"
		)
	);
	override fun locale(language: String) = locale(Locale.fromString(language))
	override fun locale(locale: Locale) = localeMap[locale] ?: localeMap[Locale.ENGLISH_GREAT_BRITAIN]!!
}