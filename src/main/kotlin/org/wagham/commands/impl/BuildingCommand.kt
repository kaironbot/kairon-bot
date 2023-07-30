package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.rest.builder.message.EmbedBuilder
import org.wagham.annotations.BotCommand
import org.wagham.commands.SlashCommandWithSubcommands
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.commands.BuildingLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.BuildingRestrictionType
import org.wagham.db.models.BaseBuilding
import org.wagham.db.models.Character
import org.wagham.db.models.ServerConfig
import org.wagham.db.pipelines.buildings.BuildingWithBounty
import org.wagham.utils.defaultLocale

@BotCommand("all")
class BuildingCommand(
    kord: Kord,
    db: KabotMultiDBClient,
    cacheManager: CacheManager
) : SlashCommandWithSubcommands(kord, db, cacheManager) {

    override val commandName = "building"
    override val defaultDescription =  BuildingLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = BuildingLocale.DESCRIPTION.localeMap

    companion object {
        fun Int.proficiencyDiscount(building: BaseBuilding, character: Character) =
            this.takeIf { _ ->
                    building.proficiencyReduction == null ||
                            !character.proficiencies
                                .map { it.name }
                                .contains(building.proficiencyReduction)
                } ?: (this / 2)

        fun describeBuildingMessage(
            building: BuildingWithBounty,
            locale: String,
            config: ServerConfig,
            character: Character?
        ): EmbedBuilder.() -> Unit {
            return fun EmbedBuilder.() {
                title = building.name
                color = Colors.DEFAULT.value

                description = buildString {
                    if(config.buildingRestrictions[BuildingRestrictionType.TYPE_RESTRICTION] != null
                        && character != null
                        && !BuildingRestrictionType.TYPE_RESTRICTION.validator(config.buildingRestrictions[BuildingRestrictionType.TYPE_RESTRICTION]!!, character, building)) {
                        append("${BuildingLocale.TYPE_LIMIT_REACHED.locale(locale)}${config.buildingRestrictions[BuildingRestrictionType.TYPE_RESTRICTION]}\n")
                    }
                    if(building.upgradeOnly) {
                        append(BuildingLocale.BY_UPGRADE_ONLY.locale(locale))
                        append("\n")
                    }
                    append("\n")
                    append("**${BuildingLocale.TYPE.locale(locale)}**: ${building.type}\n")
                    append("**Tier**: ${building.tier}\n")
                    if(building.upgradeId != null) {
                        append("**${BuildingLocale.UPGRADABLE_IN.locale(locale)}**: ${building.upgradeId}\n")
                    }
                    append("**${BuildingLocale.BUILDING_COST.locale(locale)}**: ")
                    append("${building.moCost} MO")
                    building.materials.entries.forEach { (material, qty) ->
                        append(", $material x$qty")
                    }
                    append("\n")
                    if(building.proficiencyReduction != null) {
                        append("**${BuildingLocale.BUILDING_COST_WITH_PROFICIENCY.locale(locale)} ${building.proficiencyReduction}**: ")
                        append("${building.moCost} MO")
                        building.materials.entries.forEach { (material, qty) ->
                            append(", $material x${qty/2}")
                        }
                        append("\n")
                    }
                    append("**${BuildingLocale.WEEKLY_PRIZE.locale(locale)}**\n")
                    building.bounty.prizes.forEach {
                        append(it.probability*100)
                        append("% - ")
                        if(it.moneyDelta != 0) {
                            append(it.moneyDelta)
                            append(" MO")
                        }
                        if (it.guaranteedItems.isNotEmpty()) {
                            if(it.moneyDelta != 0) append(", ")
                            append(
                                it.guaranteedItems.entries.joinToString(", ") { i -> "${i.key} x${i.value}" }
                            )
                        }
                        it.randomItems.forEach { p ->
                            append(", ")
                            append(p.itemId)
                            append("x")
                            append(p.qty)
                            append(" (")
                            append(p.probability*100)
                            append("%)")
                        }
                        append("\n")
                    }
                }
            }
        }
    }
    
}