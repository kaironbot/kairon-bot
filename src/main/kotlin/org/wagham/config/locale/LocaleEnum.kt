package org.wagham.config.locale

import dev.kord.common.Locale

interface LocaleEnum {
    fun locale(language: String): String
    fun locale(locale: Locale): String
}
