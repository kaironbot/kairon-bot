package org.wagham.sheets.data

import org.wagham.db.models.Craft
import org.wagham.db.models.Item
import org.wagham.db.models.ReputationRequirement
import org.wagham.sheets.GoogleSheetsUtils
import org.wagham.sheets.getHeaderMapping
import org.wagham.utils.formatToFloat
import org.wagham.utils.formatToInt
import org.wagham.utils.toListOfMap

class ItemRow(
    val operation: ImportOperation = ImportOperation.NONE,
    val item: Item
) {
    companion object {

        private val sheetId = System.getenv("SHEET_ID")!!
        private const val range = "ITEM_BOT!B1:Z2000"

        fun parseRows(): List<ItemRow> =
            GoogleSheetsUtils
                .downloadRawDataFromSheet(sheetId, range)
                .let { values ->
                    val header = values.getHeaderMapping()
                    values.getValues()
                        .subList(1, values.getValues().size)
                        .toListOfMap(header, 0)
                        .fold(emptyList()) { acc, it ->
                            acc + ItemRow(
                                operation = when(it["import_operation"]) {
                                    "DELETE" -> ImportOperation.DELETE
                                    "UPDATE" -> ImportOperation.UPDATE
                                    else -> ImportOperation.NONE
                                },
                                item = Item(
                                    name = it["name"]!!,
                                    sellPrice = it["sell_price"]!!.formatToFloat(),
                                    sellProficiencies = it["sell_proficiencies"]!!.split(","),
                                    sellBuildingRequirement = it["Sell_BuildingNeeded"]!!,
                                    buyPrice = it["buy_price"]!!.formatToFloat(),
                                    usable = (it["is_usable"]!!.formatToInt()) == 1,
                                    link = it["Link"]!!,
                                    category = it["category"]!!,
                                    manual = it["Sorgente"]!!,
                                    attunement = (it["Attunement"]!!.formatToInt()) == 1,
                                    giveRatio = it["give_ratio"]!!.formatToFloat(),
                                    buyReputationRequirement = ReputationRequirement(
                                        territory = it["Craft_Rep_Popolo"]!!,
                                        minValue = it["Craft_Rep_Popolo_Value"]!!.formatToInt()
                                    ).takeIf { req -> req.territory.isNotBlank() },
                                    craft = Craft(
                                        craftMoCost = it["craft_mo_cost"]!!.formatToFloat(),
                                        tier = it["Tier"]!!.takeIf { tier -> tier != "None" },
                                        craftTools = it["craft_tools"]!!.split(","),
                                        craftTBadge = it["Craft_tbadge"]!!.formatToInt(),
                                        craftTime = it["craft_time"]!!.formatToInt(),
                                        craftTotalCost = it["craft_total_cost"]!!.formatToFloat(),
                                        craftMinQty = it["craft_min_qty"]!!.formatToInt(),
                                        craftMaxQty = it["craft_max_qty"]!!.formatToInt(),
                                        craftReputationRequirement = ReputationRequirement(
                                            territory = it["Craft_Rep_Popolo"]!!,
                                            minValue = it["Craft_Rep_Popolo_Value"]!!.formatToInt()
                                        ).takeIf { req -> req.territory.isNotBlank() },
                                        buildingRequired = it["Craft_BuildingInRecipe"]!!,
                                        ingredients = it["craft_ingredients"]!!
                                            .split(",")
                                            .fold(emptyMap()) { map, ing ->
                                                val matches = Regex("(.+)x([0-9]+)").find(ing)
                                                if(matches != null && matches.groupValues.size == 3)
                                                    map + (matches.groupValues[1] to matches.groupValues[2].formatToInt())
                                                else map
                                            }
                                    )
                                )
                            )
                        }
                }



    }
}