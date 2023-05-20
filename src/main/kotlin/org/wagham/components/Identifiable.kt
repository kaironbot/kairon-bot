package org.wagham.components

interface Identifiable {

    fun buildElementId(vararg descriptors: String) = buildString {
        append(this@Identifiable::class.qualifiedName)
        append("-")
        append(descriptors.joinToString("-").takeIf { descriptors.isNotEmpty() } ?: "")
    }

}