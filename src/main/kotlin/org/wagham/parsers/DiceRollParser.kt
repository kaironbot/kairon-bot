package org.wagham.parsers

import com.github.h0tk3y.betterParse.combinators.leftAssociative
import com.github.h0tk3y.betterParse.combinators.or
import com.github.h0tk3y.betterParse.combinators.use
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.Parser
import kotlin.random.Random

data class Roll(
    val results: Map<String, List<Int>> = emptyMap(),
    val total: Int = 0
) {

    operator fun plus(other: Roll) =
        this.copy(
            results = other.results.entries.fold(results) { acc, (value, rolls) ->
                acc + (value to acc.getOrDefault(value, emptyList()) + rolls)
            },
            total = total + other.total
        )

    operator fun minus(other: Roll) =
        this.copy(
            results = other.results.entries.fold(results) { acc, (value, rolls) ->
                acc + (value to acc.getOrDefault(value, emptyList()) + rolls)
            },
            total = total - other.total
        )

}

class DiceRollParser : Grammar<Roll>() {
    val die by regexToken("[0-9]+d[0-9]+")
    val num by regexToken("[0-9]+")
    val minus by literalToken("-")
    val plus by literalToken("+")
    val ws by regexToken("\\s+", ignore = true)

    val roll by die use  {
        val (numDie, valueDie) = text.split("d")
        val rolls = List(numDie.toInt()) {
            Random.nextInt(1, valueDie.toInt()+1)
        }
        Roll(mapOf(valueDie to rolls), rolls.sum())
    }
    val modifier by num use {
        Roll(emptyMap(), text.toInt())
    }
    val sumChain by leftAssociative(modifier or roll, plus or minus use { type }) { a, op, b ->
        if (op == plus) a + b else a - b
    }

    override val rootParser: Parser<Roll> by sumChain
}