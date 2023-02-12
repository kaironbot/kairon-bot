package org.wagham.entities

data class PaginatedList<T>(
    val elements: List<T>,
    private val startIndex: Int = 0,
    private val pageSize: Int = 5
) {

    val page: List<T>
        get() = elements.subList(startIndex, elements.size).take(pageSize)

    val size: Int
        get() = elements.size

    fun isEmpty(): Boolean = elements.isEmpty()

    fun previousPage() =
        when {
            startIndex == 0 -> this
            (startIndex - pageSize) < 0 -> this.copy(startIndex = 0)
            else -> this.copy(startIndex = startIndex-pageSize)
        }

    fun nextPage() =
        when {
            (startIndex + pageSize) >= elements.size -> this
            else -> this.copy(startIndex = startIndex + pageSize)
        }

}