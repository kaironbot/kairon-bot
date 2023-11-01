package org.wagham.sheets.data

import org.wagham.db.models.Item
import org.wagham.db.models.embed.BuySellRequirement
import org.wagham.db.models.embed.CraftRequirement
import org.wagham.db.models.embed.ReputationRequirement
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
        private const val RANGE = "ITEM_BOT!B1:Z2000"

        private fun baseIngredients(tier: String?, qty: Int): Map<String, Int> =
            mapOf("1Day${tier}Badge" to qty).takeIf { tier != null && qty > 0 } ?: emptyMap()

        fun parseRows(): List<ItemRow> =
            GoogleSheetsUtils
                .downloadRawDataFromSheet(sheetId, RANGE)
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
                                    sell = BuySellRequirement(
                                            it["sell_price"]!!.formatToFloat(),
                                            it["Sell_BuildingNeeded"]!!.split(",").filter { it.isNotBlank() }.toSet(),
                                            it["sell_proficiencies"]!!.split(",").filter { it.isNotBlank() }.toSet()
                                        ).takeIf { it.cost > 0 },
                                    buy = BuySellRequirement(
                                        it["buy_price"]!!.formatToFloat(),
                                        reputation = setOfNotNull(
                                            ReputationRequirement(
                                                territory = it["Craft_Rep_Popolo"]!!,
                                                minValue = it["Craft_Rep_Popolo_Value"]!!.formatToInt()
                                            ).takeIf { req -> req.territory.isNotBlank() }
                                        )
                                    ).takeIf { it.cost > 0 },
                                    usable = (it["is_usable"]!!.formatToInt()) == 1,
                                    link = it["Link"]!!,
                                    category = it["category"]!!,
                                    manual = it["Sorgente"]!!,
                                    tier = it["Tier"]!!.takeIf { tier -> tier != "None" },
                                    attunement = (it["Attunement"]!!.formatToInt()) == 1,
                                    giveRatio = it["give_ratio"]!!.formatToFloat(),
                                    craft = listOfNotNull(CraftRequirement(
                                        it["craft_time"]!!.formatToInt().toLong().takeIf { it > 0 },
                                        it["craft_min_qty"]!!.formatToInt(),
                                        it["craft_max_qty"]!!.formatToInt(),
                                        it["craft_ingredients"]!!
                                            .split(",")
                                            .fold(baseIngredients(it["Tier"]!!.takeIf { tier -> tier != "None" }, it["Craft_tbadge"]!!.formatToInt())) { map, ing ->
                                                val matches = Regex("(.+)x([0-9]+)").find(ing)
                                                if(matches != null && matches.groupValues.size == 3)
                                                    map + (matches.groupValues[1] to matches.groupValues[2].formatToInt())
                                                else map
                                            },
                                        null,
                                        it["craft_mo_cost"]!!.formatToFloat(),
                                        it["Craft_BuildingInRecipe"]!!.split(",").filter { it.isNotBlank() }.toSet(),
                                        it["craft_tools"]!!.split(",").filter { it.isNotBlank() }.toSet(),
                                    ).takeIf { it.cost > 0 })
                                )
                            )
                        }
                }

        private fun Map<String, String>.toItem() {
            return Item(
                name = getValue("Name_Item"),
                sell = getValue("Item_Sell_Price").formatToFloat().takeIf { it > 0 }?.let {
                    BuySellRequirement(cost = it)
                },
                buy = getValue("Item_Buy_Price").formatToFloat().takeIf { it > 0 }?.let {
                    BuySellRequirement(cost = it)
                }.takeIf { getValue("Item Always purchasable?").formatToInt() == 1 },
                usable = getValue("usable?").formatToInt() == 1,
                link = getValue("Link").takeIf { it.isNotBlank() },

            )
        }
    }
}