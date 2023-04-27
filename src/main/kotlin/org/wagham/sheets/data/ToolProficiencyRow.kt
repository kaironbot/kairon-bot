package org.wagham.sheets.data

import org.wagham.db.models.ToolProficiency
import org.wagham.sheets.GoogleSheetsUtils
import org.wagham.sheets.getHeaderMapping
import org.wagham.utils.toListOfMap

class ToolProficiencyRow(
    val operation: ImportOperation = ImportOperation.UPDATE,
    val tool: ToolProficiency
) {

    companion object {

        private val sheetId = System.getenv("DND_SHEET_ID")!!
        private const val range = "Tools!A1:G100"

        fun parseRows(): List<ToolProficiencyRow> =
            GoogleSheetsUtils
                .downloadRawDataFromSheet(sheetId, range)
                .let { values ->
                    val header = values.getHeaderMapping()
                    values.getValues()
                        .subList(1, values.getValues().size)
                        .toListOfMap(header, 0)
                        .fold(emptyList()) { acc, it ->
                            acc + ToolProficiencyRow(
                                tool = ToolProficiency(
                                    id = it["ID"]!!,
                                    name = it["NAME"]!!,
                                    cost = null
                                )
                            )
                        }
                }

    }

}