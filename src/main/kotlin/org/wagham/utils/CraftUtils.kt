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

data class InvalidQuantity(val qty: Int, val min: Int?, val max: Int?)

data class MissingMaterialsReport(
    val quantity: InvalidQuantity?,
    val buildings: Set<String>?,
    val tools: Set<String>?,
    val missingMaterials: Map<String, Int>,
    val money: Float?
)

fun Item.generateMissingMaterialsReport(character: Character, amount: Int): String =
    craft.mapIndexed { index, recipe ->
        val report = MissingMaterialsReport(
            InvalidQuantity(amount, recipe.minQuantity, recipe.maxQuantity).takeIf { !recipe.quantityIsValid(amount) },
            recipe.buildings.takeIf { !recipe.characterHasBuildingRequirement(character) },
            recipe.tools.takeIf { !recipe.characterHasProficiencyRequirement(character) },
            recipe.materials.filter { (k, v) ->
                (v * amount) - character.inventory.getOrDefault(k, 0) >= 0
            }.map { (k, v) ->
                k to ((v * amount) - character.inventory.getOrDefault(k, 0)).toInt()
            }.toMap(),
            ((recipe.cost * amount) - character.money).takeIf { it > 0 }
        )
        (recipe.label ?: "Recipe $index") to report
    }.toMap().let { report ->
        buildString {
            append("You are missing some requirements on all the recipes.\n")
            report.forEach { (recipeId, materials) ->
                append("**$recipeId**:\n")
                if(materials.quantity != null) {
                    append("\tQuantity ${materials.quantity.qty} is invalid:")
                    materials.quantity.max?.also {
                        append(" max is $it")
                    }
                    materials.quantity.min?.also {
                        append(" min is $it")
                    }
                    append("\n")
                }
                materials.buildings?.also {
                    append("\tMissing buildings: ")
                    append(it.joinToString(", "))
                    append("\n")
                }
                materials.tools?.also {
                    append("\tMissing tools: ")
                    append(it.joinToString(", "))
                    append("\n")
                }
                if(materials.missingMaterials.isNotEmpty()) {
                    append("\tMissing materials: ")
                    append(materials.missingMaterials.entries.joinToString(", ") { "${it.key} x${it.value}" })
                    append("\n")
                }
                materials.money?.also {
                    append("\tMissing money: $it\n")
                }
                append("\n")
            }
        }
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