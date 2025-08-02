package org.wagham.exceptions

class UnauthorizedException(msg: String? = null) : Exception(msg ?: "You are not authorized to execute this command")