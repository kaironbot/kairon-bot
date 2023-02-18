package org.wagham.sheets.data

import org.wagham.db.models.BuildingRecipe
import org.wagham.sheets.GoogleSheetsUtils
import org.wagham.sheets.getHeaderMapping
import org.wagham.utils.formatToInt
import org.wagham.utils.toListOfMap

class BuildingRecipeRow(
    val operation: ImportOperation = ImportOperation.NONE,
    val buildingRecipe: BuildingRecipe
) {

    companion object {

        private val sheetId = System.getenv("SHEET_ID")!!
        private const val range = "EDIFICI_BOT!A1:I40"

        fun parseRows(): List<BuildingRecipeRow> =
            GoogleSheetsUtils
                .downloadRawDataFromSheet(sheetId, range)
                .let { values ->
                    val header = values.getHeaderMapping()
                    values.getValues()
                        .subList(1, values.getValues().size)
                        .toListOfMap(header, 0)
                        .fold(emptyList()) { acc, it ->
                            acc + BuildingRecipeRow(
                                operation = when(it["import_operation"]) {
                                    "DELETE" -> ImportOperation.DELETE
                                    "UPDATE" -> ImportOperation.UPDATE
                                    else -> ImportOperation.NONE
                                },
                                buildingRecipe = BuildingRecipe(
                                    name = it["IDEdificio"]!!,
                                    moCost = it["Costo mo craft"]!!.formatToInt(),
                                    tbadgeCost = it["Costo Tbadge"]!!.formatToInt(),
                                    tbadgeType = "1Day${it["IDEdificio"]!!.split(" ".last())}Badge",
                                    proficiencyReduction = it["Proficiency_Tbadge_Discount"]!!.takeIf { it.isNotBlank() },
                                    bountyId = it["IDPremio"]!!,
                                    size = it["Dimensioni mappa (5feetx5Feet)"]!!,
                                    areas = it["Zona massima ammessa"]!!.split(","),
                                    maxDescriptionSize = it["Lunghezza Custom Edificio"]!!.formatToInt()
                                )
                            )
                        }
                }

    }

}