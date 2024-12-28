package org.wagham.utils

import org.wagham.db.utils.daysInBetween
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

fun getStartingInstantOnNextDay(hour: Int, minute: Int, second: Int, transformer: (LocalDateTime) -> LocalDateTime = { it }): Date {
	val timeZone = TimeZone.getTimeZone("GMT+2")
	val calendar = Calendar.getInstance(timeZone)
	val localDate = LocalDateTime.of(
		calendar.get(Calendar.YEAR),
		calendar.get(Calendar.MONTH)+1,
		calendar.get(Calendar.DAY_OF_MONTH),
		hour + timeZone.dstSavings,
		minute,
		second
	).plusDays(1).let(transformer)

	return Date.from(ZonedDateTime.of(localDate, timeZone.toZoneId()).toInstant())
}

fun daysToToday(pastDate: Date) = daysInBetween(pastDate, Calendar.getInstance().time).toInt()

fun maxOrNull(first: Date?, second: Date?): Date? =
	when {
		first == null && second == null -> null
		first == null -> second
		second == null -> first
		first > second -> first
		else -> second
	}


/**
 * @return the offset, in minutes, between the current timezone and the UTC timezone
 */
fun getTimezoneOffset(): Long {
	val calendar = Calendar.getInstance()
	val timeZone = calendar.timeZone
	val isDaylightTime = timeZone.inDaylightTime(Date())
	return timeZone.rawOffset.milliseconds.inWholeMinutes + (60.takeIf { isDaylightTime } ?: 0)
}
