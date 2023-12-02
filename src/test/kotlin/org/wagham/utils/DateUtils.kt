package org.wagham.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DateUtils : StringSpec({

    "Can get the timezone offset" {
        getTimezoneOffset() shouldBe 120
    }

})