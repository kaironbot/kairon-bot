package org.wagham.sheets.data

import org.wagham.db.models.Background
import org.wagham.sheets.GoogleSheetsUtils
import org.wagham.sheets.getHeaderMapping
import org.wagham.utils.toListOfMap

class BackgroundRow(
    val operation: ImportOperation = ImportOperation.UPDATE,
    val background: Background
) {

    companion object {

        private val sheetId = System.getenv("DND_SHEET_ID")!!
        private const val range = "All_Bg!A1:C100"

        fun parseRows(): List<BackgroundRow> =
            GoogleSheetsUtils
                .downloadRawDataFromSheet(sheetId, range)
                .let { values ->
                    val header = values.getHeaderMapping()
                    values.getValues()
                        .subList(1, values.getValues().size)
                        .toListOfMap(header, 0)
                        .fold(emptyList()) { acc, it ->
                            acc + BackgroundRow(
                                background = Background(
                                    name = it["Nome_Bg"]!!,
                                    race = it["Razziale"]!!,
                                    link = it["Link"]!!,
                                )
                            )
                        }
                }

    }

}