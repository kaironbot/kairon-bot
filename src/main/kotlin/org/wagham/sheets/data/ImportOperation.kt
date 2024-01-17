package org.wagham.sheets.data

enum class ImportOperation {
    UPDATE,
    DELETE,
    DISCARDED,
    WIP,
    MG,
    ERROR,
    NONE;

    companion object {
        fun valueOfNone(input: String) = runCatching {
            ImportOperation.valueOf(input.uppercase())
        }.onFailure {
            NONE
        }.getOrDefault(NONE)

    }
}