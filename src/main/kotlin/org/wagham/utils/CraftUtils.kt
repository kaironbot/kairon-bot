package org.wagham.utils

import org.wagham.db.models.Character
import org.wagham.db.models.Item
import org.wagham.db.models.embed.CraftRequirement

fun CraftRequirement.quantityIsValid(amount: Int): Boolean =
    (minQuantity == null || amount >= minQuantity!!) && (maxQuantity == null || amount <= maxQuantity!!)

fun CraftRequirement.characterHasBuildingRequirement(character: Character) =
    buildings.isEmpty() || character.buildings.keys.map { it.split(":").first() }.any {
        buildings.contains(it)
    }

fun CraftRequirement.characterHasProficiencyRequirement(character: Character) =
    tools.isEmpty() || character.proficiencies.map { it.name }.any { tools.contains(it) }

fun CraftRequirement.characterHasEnoughMaterials(character: Character, amount: Int) =
   materials.mapValues {
        (it.value * amount) - character.inventory.getOrDefault(it.key, 0)
    }.all { (_, v) -> v <= 0 }

fun Item.selectCraftOptions(character: Character, amount: Int): List<Int> =
    craft.mapIndexedNotNull { index, recipe ->
        if(recipe.quantityIsValid(amount)
            && recipe.characterHasBuildingRequirement(character)
            && recipe.characterHasProficiencyRequirement(character)
            && recipe.characterHasEnoughMaterials(character, amount)
            && character.money >= (recipe.cost * amount)) index to recipe
        else null
    }.groupBy { (_, recipe) ->
        setOf(
            "${recipe.cost}",
            "${recipe.timeRequired}"
        ) + recipe.materials.map { (k, v) -> "${k}x$v" }.toSet()
    }.mapNotNull { (_, recipes) ->
        recipes.firstOrNull()?.first
    }

fun CraftRequirement.summary() = buildString {
    append(this@summary.cost)
    append(" MO, ")
    val minQty = this@summary.minQuantity
    val maxQty = this@summary.maxQuantity
    append(this@summary.materials.entries.joinToString(", ") {
        if(minQty == maxQty && minQty != null) "${it.key} x${(it.value * minQty).toInt()}"
        else "${it.key} x${it.value}"
    })
    this@summary.timeRequired?.also {
        append(" - $it hours")
    } ?: append(" - Instantaneous")
    if(minQty == maxQty && minQty != null) {
        append(" - Qty: $minQty")
    } else {
        this@summary.minQuantity?.also {
            append(" - Min Qty: $it")
        }
        this@summary.maxQuantity?.also {
            append(" - Max Qty: $it")
        }
    }
    if(this@summary.buildings.isNotEmpty()) {
        append(" - Buildings: ")
        append(this@summary.buildings.joinToString(", "))
    }
    if(this@summary.tools.isNotEmpty()) {
        append(" - Tool: ")
        append(this@summary.tools.joinToString(", "))
    }
}