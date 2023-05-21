package org.wagham.utils

import java.util.UUID

// Thanks to https://gist.github.com/ademar111190/34d3de41308389a0d0d8
fun levenshtein(lhs : CharSequence, rhs : CharSequence) : Int {
    val lhsLength = lhs.length + 1
    val rhsLength = rhs.length + 1

    var cost = Array(lhsLength) { it }
    var newCost = Array(lhsLength) { 0 }

    for (i in 1 until rhsLength) {
        newCost[0] = i

        for (j in 1 until lhsLength) {
            val match = if(lhs[j - 1] == rhs[i - 1]) 0 else 1

            val costReplace = cost[j - 1] + match
            val costInsert = cost[j] + 1
            val costDelete = newCost[j - 1] + 1

            newCost[j] = costInsert.coerceAtMost(costDelete).coerceAtMost(costReplace)
        }

        val swap = cost
        cost = newCost
        newCost = swap
    }

    return cost[lhsLength - 1]
}

fun String.levenshteinDistance(other:String) =
    levenshtein(this.subSequence(0, this.length), other.subSequence(0, other.length))
        .takeIf { it > 0 }
        ?.let { 1.0 / it } ?: 1.0

fun List<List<Any>>.toListOfMap(header: Map<String, Int>, guaranteedIndex: Int? = null): List<Map<String, String>> =
    this.filter {
            guaranteedIndex == null ||
                    (guaranteedIndex < it.size &&
                            !(it[guaranteedIndex] as? String).isNullOrBlank())
        }.fold(emptyList()) { finalList, it ->
            finalList + header.keys.fold(emptyMap()) { acc, key ->
                if(header[key]!! < it.size)
                    acc + (key to (it[header[key]!!] as String))
                else acc + (key to "")
            }
        }

fun String.formatToFloat() = try {
        this.replace(",", ".").toFloat()
    } catch(e: NumberFormatException) {
        0.0f
    }

fun String.formatToInt() = try { this.toInt() } catch(e: NumberFormatException) { 0 }

fun uuid() = UUID.randomUUID().toString()

val transactionMoney = "MONEY"