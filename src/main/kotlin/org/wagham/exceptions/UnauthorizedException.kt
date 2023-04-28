package org.wagham.exceptions

class UnauthorizedException(val msg: String? = null) : Exception(msg ?: "You are not authorized to execute this command")