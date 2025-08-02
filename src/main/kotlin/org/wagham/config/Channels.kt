package org.wagham.config

enum class Channels(val description: String) {
    ATTENDANCE_CHANNEL("Attendance message channel"),
    LOG_CHANNEL("Logging channel"),
    MESSAGE_CHANNEL("Main message channel"),
    BUILDINGS_CHANNEL("Buildings channel"),
    BOT_CHANNEL("Main bot channel"),
    WELCOME_CHANNEL("Welcome channel"),
    MARKET_CHANNEL("Market channel"),
    MASTER_CHANNEL("Master channel"),
}