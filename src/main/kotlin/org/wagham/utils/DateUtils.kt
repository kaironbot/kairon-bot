package org.wagham.utils

import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.*

fun getStartingInstantOnNextDay(hour: Int, minute: Int, second: Int, transformer: (LocalDateTime) -> LocalDateTime = { it }): Date {
    val timeZone = TimeZone.getTimeZone("GMT+2")
    val calendar = Calendar.getInstance(timeZone)
    val localDate = LocalDateTime.of(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH)+1,
        calendar.get(Calendar.DAY_OF_MONTH)+1,
        hour + timeZone.dstSavings,
        minute,
        second
    ).let(transformer)

    return Date.from(ZonedDateTime.of(localDate, timeZone.toZoneId()).toInstant())
}

