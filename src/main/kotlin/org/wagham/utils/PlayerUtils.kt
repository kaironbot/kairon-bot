package org.wagham.utils

import org.wagham.db.models.Player
import org.wagham.db.models.embed.Strike
import java.time.LocalDate
import java.time.temporal.ChronoUnit

val Player.recentStrikes: List<Strike>
	get() {
		return strikes.filter {
			daysToToday(it.date) <= 60
		}
	}

val Player.latestStrike: Strike
	get() = strikes.maxBy { it.date.toInstant() }
