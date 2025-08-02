package org.wagham.exceptions

import java.time.LocalDate


class BannedException(
	val until: LocalDate,
	msg: String? = null
) : Exception(msg ?: "You cannot execute this command because you are banned until $until")