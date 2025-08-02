package org.wagham.utils

import org.wagham.db.models.Player
import org.wagham.db.models.embed.Strike
import java.time.LocalDate
import java.time.temporal.ChronoUnit

val Player.recentStrikes: List<Strike>
	get() {
		val now = LocalDate.now()
		return strikes.filter {
			ChronoUnit.DAYS.between(it.date.toInstant(), now) <= 60
		}
	}

val Player.latestStrike: Strike
	get() = strikes.maxBy { it.date.toInstant() }
