package org.wagham.sheets.data

import org.wagham.db.models.Subclass
import org.wagham.sheets.GoogleSheetsUtils
import org.wagham.sheets.getHeaderMapping
import org.wagham.utils.toListOfMap

class DndClassRow(
    val operation: ImportOperation = ImportOperation.UPDATE,
    val dndClass: Subclass
) {

    companion object {

        private val sheetId = System.getenv("DND_SHEET_ID")!!
        private const val range = "All_Class!A1:F200"

        fun parseRows(): List<DndClassRow> =
            GoogleSheetsUtils
                .downloadRawDataFromSheet(sheetId, range)
                .let { values ->
                    val header = values.getHeaderMapping()
                    values.getValues()
                        .subList(1, values.getValues().size)
                        .toListOfMap(header, 0)
                        .foldIndexed(emptyList()) { idx, acc, it ->
                            acc + DndClassRow(
                                dndClass = Subclass(
                                    id = idx,
                                    dndClass = it["Classe"]!!,
                                    subclass = it["Sottoclasse"]!!,
                                    race = it["Razza"]!!.split(","),
                                    territory = it["Popolo"]!!,
                                    link = it["Link"]!!,
                                    source = it["Manuale"]!!
                                )
                            )
                        }
                }

    }

}