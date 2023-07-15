package org.wagham.components

interface Identifiable {

    fun buildElementId(vararg descriptors: Any) = buildString {
        append(this@Identifiable::class.qualifiedName)
        append("-")
        append(descriptors.joinToString("-") { it.toString() }.takeIf { descriptors.isNotEmpty() } ?: "")
    }

    fun verifyId(id: String, vararg descriptors: String) = id.startsWith(buildElementId(*descriptors))
}