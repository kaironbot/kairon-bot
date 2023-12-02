package org.wagham.sheets.data

import org.wagham.db.models.ToolProficiency
import org.wagham.db.models.embed.AbilityCost
import org.wagham.db.models.embed.LabelStub
import org.wagham.sheets.GoogleSheetsUtils
import org.wagham.sheets.getHeaderMapping
import org.wagham.utils.formatToFloat
import org.wagham.utils.formatToInt
import org.wagham.utils.toListOfMap

class ToolProficiencyRow(
    val operation: ImportOperation = ImportOperation.UPDATE,
    val tool: ToolProficiency
) {

    companion object {

        private val sheetId = System.getenv("SHEET_ID")!!
        private const val RANGE = "Tools!A1:G100"

        fun parseRows(): List<ToolProficiencyRow> =
            GoogleSheetsUtils
                .downloadRawDataFromSheet(sheetId, RANGE)
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
                                    cost = AbilityCost(
                                        it["MONEY"]!!.formatToFloat(),
                                        listOfNotNull(
                                            it["ITEM"].takeIf { name -> !name.isNullOrBlank() }?.let { item ->
                                                item to it["QTY"]!!.formatToInt()
                                            }
                                        ).toMap()
                                    ),
                                    labels = setOf(LabelStub("5dae89f9-f041-4e0a-a9ce-f450586f04d1", "Craft")).takeIf { _ ->
                                        it["CRAFT"] == "TRUE"
                                    } ?: emptySet()
                                )
                            )
                        }
                }

    }

}