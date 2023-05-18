package org.wagham.parsers

import com.github.h0tk3y.betterParse.grammar.parseToEnd
import io.kotest.core.spec.style.StringSpec

class DiceRollParserTest : StringSpec({

    "Can parse a roll" {
        val roll = "1d20+3-1d4-2+1d4-1d20"
        val result = DiceRollParser().parseToEnd(roll)
        println(result)
    }

})