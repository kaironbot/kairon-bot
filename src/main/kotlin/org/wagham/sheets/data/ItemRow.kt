package org.wagham.sheets.data

import org.wagham.db.models.Item
import org.wagham.db.models.embed.BuySellRequirement
import org.wagham.db.models.embed.CraftRequirement
import org.wagham.db.models.embed.Label
import org.wagham.db.models.embed.LabelStub
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
        private const val RANGE = "All_Item!A1:AN2000"
        private val classes = listOf(
            "Artificer", "Barbarian", "Bard", "Blood Hunter", "Cleric", "Druid", "Fighter", "Monk", "Paladin", "Ranger", "Rogue", "Sorcerer", "Warlock", "Wizard"
        )

        fun parseRows(labelsByName: Map<String, Label>): List<ItemRow> =
            GoogleSheetsUtils
                .downloadRawDataFromSheet(sheetId, RANGE)
                .let { values ->
                    val header = values.getHeaderMapping()
                    val recipes = mutableSetOf<String>()
                    values.getValues()
                        .subList(1, values.getValues().size)
                        .toListOfMap(header, 0)
                        .fold(emptyList()) { acc, it ->
                            val operation = ImportOperation.valueOf(it.getValue("import_operation"))
                            acc + when(operation) {
                                ImportOperation.DISCARDED -> emptyList()
                                else -> listOfNotNull(
                                    ItemRow(operation = operation, item = it.toItem(acc, labelsByName)),
                                    it.toRecipeOrNull(labelsByName)?.let { recipe ->
                                        ItemRow(operation = operation, item = recipe)
                                    }?.takeIf { r -> !recipes.contains(r.item.name) }?.also {
                                        recipes.add(it.item.name)
                                    }
                                )
                            }
                        }
                }

        private fun Map<String, String>.toItem(alreadyParsed: List<ItemRow>, labelsByName: Map<String, Label>): Item =
            Item(
                name = getValue("Name_Item").trim(),
                sell = getValue("Item_Sell_Price").formatToFloat().takeIf { it > 0 }?.let {
                    BuySellRequirement(cost = it)
                }.takeIf { getValue("sellable?").formatToInt() == 1 },
                buy = getValue("Item_Buy_Price").formatToFloat().takeIf { it > 0 }?.let {
                    BuySellRequirement(cost = it)
                }.takeIf { getValue("Item Always purchasable?").formatToInt() == 1 },
                usable = getValue("usable?").formatToInt() == 1,
                link = getValue("Link").takeIf { it.isNotBlank() },
                manual = getValue("Source").takeIf { it.isNotBlank() },
                attunement = getValue("Attunement").formatToInt() == 1,
                category = getValue("Category").trim().takeIf { it.isNotBlank() },
                giveRatio = 1.0f,
                craft = listOfNotNull(
                    extractRecipeCraftOrNull(),
                    *extractUpgradeCraftOrNull(alreadyParsed)
                ),
                labels = extractLabels(labelsByName)
            )

        private fun Map<String, String>.toRecipeOrNull(labelsByName: Map<String, Label>) =
            if(getValue("Name_Recipe").trim().isNotBlank()) {
                Item(
                    name = getValue("Name_Recipe").trim(),
                    sell = getValue("Recipe_Sell_Price").formatToFloat().takeIf { it > 0 }?.let {
                        BuySellRequirement(cost = it)
                    }.takeIf { getValue("sellable?").formatToInt() == 1 },
                    category = "Recipe",
                    giveRatio = 0.0f,
                    labels = extractLabels(labelsByName) + labelsByName.getValue("Recipe").toLabelStub()
                )
            } else null

        private fun Map<String, String>.extractLabels(labelsByName: Map<String, Label>): Set<LabelStub> =
            setOfNotNull(
                "[^(]+(T[0-9]).*".toRegex().find(getValue("Category"))?.groupValues?.get(1)?.let { labelsByName.getValue(it).toLabelStub() },
                labelsByName.getValue("Consumable").toLabelStub().takeIf { getValue("usable?").formatToInt() == 1 },
                getValue("Drop_Tool").takeIf { it != "Nessuno" }?.let { labelsByName.getValue(it.trim()).toLabelStub() },
                labelsByName.getValue("Market").toLabelStub().takeIf { getValue("Weekly market?").formatToInt() == 1 },
                labelsByName.getValue("SessionPrize").toLabelStub().takeIf { getValue("Weekly market?").formatToInt() == 1 },
                getValue("Type").let { labelsByName.getValue(it.trim()).toLabelStub() },
                *classes.map { dndClass ->
                    labelsByName.getValue(dndClass).toLabelStub().takeIf { getValue(dndClass).formatToInt() == 1 }
                }.toTypedArray()
            )

        private fun Map<String, String>.extractRecipeCraftOrNull() = if(getValue("Name_Recipe").isNotBlank()) {
            CraftRequirement(
                timeRequired = null,
                minQuantity = getValue("Craft_Quantity").formatToInt(),
                maxQuantity = getValue("Craft_Quantity").formatToInt(),
                materials = mapOf(getValue("Name_Recipe") to 1),
                label = "Craft",
                cost = getValue("Craft_Mo_Cost").formatToFloat()
            )
        } else null

        private fun Map<String, String>.extractUpgradeCraftOrNull(alreadyParsed: List<ItemRow>) = if(getValue("Upgrade from?").trim().isNotBlank()) {
            getValue("Upgrade from?").trim().split("|").map { it.trim() }.map { itemName ->
                val baseItem = alreadyParsed.first {
                        it.item.name == itemName
                    }
                CraftRequirement(
                    timeRequired = null,
                    minQuantity = 1,
                    maxQuantity = 1,
                    materials = mapOf(baseItem.item.name to 1),
                    label = "Upgrade $itemName",
                    cost = baseItem.item.craft.firstOrNull { it.label == "Craft" }?.cost ?: baseItem.item.buy?.cost ?: 0.0f
                )
            }.toTypedArray()
        } else emptyArray()
    }
}