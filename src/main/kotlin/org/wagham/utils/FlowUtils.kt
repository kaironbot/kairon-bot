package org.wagham.utils

import kotlinx.coroutines.flow.Flow

/**
 * Collects a [Flow] associating each element to the result of the block function passed as parameter.
 * If an element is present more than once, then it will be overwritten in the map.
 *
 * @receiver a [Flow]
 * @param block a function that will take each element and return the value to insert in the map.
 * @return a [Map] that associates to each value in the [Flow] the computed value.
 */
suspend fun <T, R> Flow<T>.associateTo(block: suspend (T) -> R): Map<T, R> {
    val res = mutableMapOf<T, R>()
    this.collect {
        res[it] = block(it)
    }
    return res
}

/**
 * Collects a [Flow] associating the result of the block function passed as parameter to each element.
 * If a key is present more than once, then it will be overwritten in the map.
 *
 * @receiver a [Flow]
 * @param block a function that will take each element and return the corresponding key in the map.
 * @return a [Map] that associates the computed value to each value in the [Flow].
 */
suspend fun <T, R> Flow<T>.associateBy(block: suspend (T) -> R): Map<R, T> {
    val res = mutableMapOf<R, T>()
    this.collect {
        res[block(it)] = it
    }
    return res
}

