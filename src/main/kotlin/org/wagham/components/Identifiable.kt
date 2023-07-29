package org.wagham.components

const val defaultSeparator = "-"

interface Identifiable {

    fun buildElementId(vararg descriptors: Any) = buildString {
        append(this@Identifiable::class.qualifiedName)
        append(defaultSeparator)
        append(descriptors.joinToString("-") { it.toString() }.takeIf { descriptors.isNotEmpty() } ?: "")
    }

    fun verifyId(id: String, vararg descriptors: String) = id.startsWith(buildElementId(*descriptors))

    fun extractComponentsFromInteractionId(id: String) = id.split(defaultSeparator).drop(1)
}