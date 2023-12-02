package org.wagham.sheets.data

import org.wagham.db.models.LanguageProficiency
import org.wagham.db.models.embed.AbilityCost
import org.wagham.sheets.GoogleSheetsUtils
import org.wagham.sheets.getHeaderMapping
import org.wagham.utils.formatToFloat
import org.wagham.utils.formatToInt
import org.wagham.utils.toListOfMap

class LanguageProficiencyRow(
    val operation: ImportOperation = ImportOperation.UPDATE,
    val language: LanguageProficiency
) {

    companion object {

        private val sheetId = System.getenv("SHEET_ID")!!
        private const val RANGE = "Languages!A1:G100"

        fun parseRows(): List<LanguageProficiencyRow> =
            GoogleSheetsUtils
                .downloadRawDataFromSheet(sheetId, RANGE)
                .let { values ->
                    val header = values.getHeaderMapping()
                    values.getValues()
                        .subList(1, values.getValues().size)
                        .toListOfMap(header, 0)
                        .fold(emptyList()) { acc, it ->
                            acc + LanguageProficiencyRow(
                                language = LanguageProficiency(
                                    id = it["ID"]!!,
                                    name = it["NAME"]!!,
                                    cost = AbilityCost(
                                        it["MONEY"]!!.formatToFloat(),
                                        listOfNotNull(
                                            it["ITEM"]?.let { item ->
                                                item to it["QTY"]!!.formatToInt()
                                            }
                                        ).toMap(),
                                        it["TIME_REQUIRED"]?.toLongOrNull()
                                    )
                                )
                            )
                        }
                }

    }

}