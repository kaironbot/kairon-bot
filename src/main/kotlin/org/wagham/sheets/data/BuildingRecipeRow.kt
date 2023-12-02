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
        private const val RANGE = "EDIFICI_BOT!A1:M40"

        fun parseRows(): List<BuildingRecipeRow> =
            GoogleSheetsUtils
                .downloadRawDataFromSheet(sheetId, RANGE)
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
                                    type = it["TIPO"]!!,
                                    tier = it["TIER"]!!,
                                    materials = mapOf(
                                        "1Day${it["TIER"]!!}Badge" to
                                                it["Costo Tbadge"]!!.formatToInt()
                                    ),
                                    upgradeId = it["UPGRADE_ID"]!!.takeIf { it.isNotBlank() },
                                    upgradeOnly = it["UPGRADE_ONLY"]!! == "TRUE",
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