package org.wagham.exceptions

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val formatter = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

class BannedException(
	val until: Date,
	msg: String? = null
) : Exception(msg ?: "You cannot execute this command because you are banned until ${formatter.format(until)}")