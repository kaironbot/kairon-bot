package org.wagham.sheets.data

import org.wagham.db.models.Feat
import org.wagham.sheets.GoogleSheetsUtils
import org.wagham.sheets.getHeaderMapping
import org.wagham.utils.toListOfMap

class FeatRow(
    val operation: ImportOperation = ImportOperation.UPDATE,
    val feat: Feat
) {

    companion object {

        private val sheetId = System.getenv("DND_SHEET_ID")!!
        private const val range = "All_Feats!A1:E200"

        fun parseRows(): List<FeatRow> =
            GoogleSheetsUtils
                .downloadRawDataFromSheet(sheetId, range)
                .let { values ->
                    val header = values.getHeaderMapping()
                    values.getValues()
                        .subList(1, values.getValues().size)
                        .toListOfMap(header, 0)
                        .fold(emptyList()) { acc, it ->
                            acc + FeatRow(
                                feat = Feat(
                                    name = it["Nome_Talento"]!!,
                                    race = it["Elenco Razze"]!!.split(","),
                                    asi = it["ASI"] == "TRUE",
                                    link = it["Link"]!!,
                                    source = it["Manuale"]!!
                                )
                            )
                        }
                }

    }

}