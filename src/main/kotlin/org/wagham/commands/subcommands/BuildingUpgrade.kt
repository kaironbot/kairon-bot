package org.wagham.commands.subcommands

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.Locale
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.SelectMenuInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.component.option
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.actionRow
import dev.kord.rest.builder.message.embed
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.BuildingCommand
import org.wagham.commands.impl.BuildingCommand.Companion.proficiencyDiscount
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.commands.BuildingLocale
import org.wagham.config.locale.subcommands.BuildingUpgradeLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.BuildingRestrictionType
import org.wagham.db.enums.TransactionType
import org.wagham.db.models.BaseBuilding
import org.wagham.db.models.Character
import org.wagham.db.models.ServerConfig
import org.wagham.db.models.embed.Transaction
import org.wagham.db.pipelines.buildings.BuildingWithBounty
import org.wagham.utils.*
import java.util.*
import java.util.concurrent.TimeUnit

@BotSubcommand("all", BuildingCommand::class)
class BuildingUpgrade(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    companion object {
        private const val BUILDING_TYPE = "building_type"
        private const val CONFIRM_UPGRADE = "confirm_upgrade"
        private data class InteractionData(
            val userId: Snowflake,
            val character: Character,
            val building: BaseBuilding? = null,
            val upgradeTo: BaseBuilding? = null,
            val existingBuilding: String? = null
        )
    }

    override val commandName = "upgrade"
    override val defaultDescription = BuildingUpgradeLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = BuildingUpgradeLocale.DESCRIPTION.localeMap
    private val interactionCache: Cache<String, InteractionData> =
        Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .build()
    override fun create(ctx: RootInputChatBuilder) {
        ctx.subCommand(commandName, defaultDescription) {
            localeDescriptions.forEach { (locale, description) ->
                description(locale, description)
            }
        }
    }

    private suspend fun handleSelection() =
        kord.on<SelectMenuInteractionCreateEvent> {
            if(verifyId(interaction.componentId, BUILDING_TYPE)) {
                val params = interaction.extractCommonParameters()
                val (_, id) = extractComponentsFromComponentId(interaction.componentId)
                val data = interactionCache.getIfPresent(id)
                when {
                    data == null -> interaction.updateWithExpirationError(params.locale)
                    data.userId != params.responsible.id -> interaction.respondWithForbiddenError(params.locale)
                    else -> {
                        val (buildingName, buildingType) = data.character.buildingWithType(interaction.values.first())
                        val buildings = cacheManager.getCollectionOfType<BuildingWithBounty>(params.guildId)
                        val config = cacheManager.getConfig(params.guildId)
                        val building = buildings.first { it.name == buildingType }
                        val upgradeBuilding = buildings.first { it.name == building.upgradeId!! }
                        interactionCache.put(id, data.copy(
                            building = building,
                            upgradeTo = upgradeBuilding,
                            existingBuilding = buildingName
                        ))
                        interaction.deferPublicMessageUpdate().edit {
                            embed {
                                title = buildingName
                                color = Colors.DEFAULT.value

                                description = buildString {
                                    if(config.buildingRestrictions[BuildingRestrictionType.TYPE_RESTRICTION] != null
                                        && !BuildingRestrictionType.TYPE_RESTRICTION.validator(config.buildingRestrictions[BuildingRestrictionType.TYPE_RESTRICTION]!!+1, data.character, building)) {
                                        append("${BuildingLocale.TYPE_LIMIT_REACHED.locale(params.locale)}${config.buildingRestrictions[BuildingRestrictionType.TYPE_RESTRICTION]}\n\n")
                                    }
                                    append("**${BuildingUpgradeLocale.TYPE.locale(params.locale)}**: ${building.name}\n")
                                    append("**${BuildingUpgradeLocale.UPGRADE_TO.locale(params.locale)}**: ${building.upgradeId}\n")
                                    append("**${BuildingUpgradeLocale.UPGRADE_COST.locale(params.locale)}**: ")
                                    append(upgradeBuilding.moCost - building.moCost)
                                    append(" MO")
                                    building.upgradeCostItems(upgradeBuilding).forEach { (item, qty) ->
                                        append(", $item x$qty")
                                    }
                                    append("\n")
                                    if(upgradeBuilding.proficiencyReduction != null) {
                                        append("**${BuildingUpgradeLocale.UPGRADE_COST_WITH_PROFICIENCY.locale(params.locale)} ${upgradeBuilding.proficiencyReduction}**: ")
                                        append(upgradeBuilding.moCost - building.moCost)
                                        append(" MO")
                                        building.upgradeCostItems(upgradeBuilding).forEach { (item, qty) ->
                                            append(", $item x${qty/2}")
                                        }
                                        append("\n")
                                    }
                                }
                            }
                            data.character.upgradableBuildings(buildings).chunked(24) { buildingsChunk ->
                                actionRow {
                                    stringSelect(buildElementId(BUILDING_TYPE, id)) {
                                        buildingsChunk.forEach {
                                            option(it.name, it.name)
                                        }
                                    }
                                }
                            }
                            actionRow {
                                interactionButton(ButtonStyle.Primary, buildElementId(CONFIRM_UPGRADE, id)) {
                                    label = BuildingUpgradeLocale.UPGRADE.locale(params.locale)
                                    disabled = !data.character.canUpgrade(building, upgradeBuilding, config)
                                }
                            }
                        }
                    }
                }
            }
        }

    private suspend fun handleUpgrade() =
        kord.on<ButtonInteractionCreateEvent> {
            if(verifyId(interaction.componentId, CONFIRM_UPGRADE)) {
                val params = interaction.extractCommonParameters()
                val (_, id) = extractComponentsFromComponentId(interaction.componentId)
                val data = interactionCache.getIfPresent(id)
                when {
                    data == null -> interaction.updateWithExpirationError(params.locale)
                    data.userId != params.responsible.id -> interaction.respondWithForbiddenError(params.locale)
                    data.building != null && data.upgradeTo != null && data.existingBuilding != null -> {
                        val config = cacheManager.getConfig(params.guildId)
                        if(!data.character.canUpgrade(data.building, data.upgradeTo, config)) {
                            interaction.deferPublicResponse().respond(createGenericEmbedError("Not enough resources"))
                        }
                        else {
                            val existingBuilding = data.character.buildings.values.flatten().first { it.name == data.existingBuilding }
                            db.transaction(params.guildId.toString()) { session ->
                                val moneyStep = db.charactersScope.subtractMoney(
                                    session,
                                    params.guildId.toString(),
                                    data.character.id,
                                    (data.upgradeTo.moCost - data.building.moCost).toFloat())
                                val materialStep = data.building.upgradeCostItems(data.upgradeTo).entries.all { (material, qty) ->
                                    db.charactersScope.removeItemFromInventory(
                                        session,
                                        params.guildId.toString(),
                                        data.character.id,
                                        material,
                                        qty.proficiencyDiscount(data.upgradeTo, data.character))
                                }
                                val removalStep = db.charactersScope.removeBuilding(
                                    session,
                                    params.guildId.toString(),
                                    data.character.id,
                                    data.existingBuilding,
                                    data.building
                                )
                                val buildingStep = db.charactersScope.addBuilding(
                                    session,
                                    params.guildId.toString(),
                                    data.character.id,
                                    existingBuilding,
                                    data.upgradeTo
                                )

                                val ingredients = data.building.upgradeCostItems(data.upgradeTo)
                                    .mapValues { it.value.proficiencyDiscount(data.upgradeTo, data.character).toFloat() } +
                                        mapOf(
                                            transactionMoney to (data.upgradeTo.moCost - data.building.moCost).toFloat(),
                                            data.building.name to 1f
                                        )

                                val transactionsStep =
                                    db.characterTransactionsScope.addTransactionForCharacter(
                                        session, params.guildId.toString(), data.character.id, Transaction(
                                            Date(),
                                            null,
                                            "UPGRADE_BUILDING",
                                            TransactionType.REMOVE,
                                            ingredients
                                        )
                                    ) && db.characterTransactionsScope.addTransactionForCharacter(
                                        session, params.guildId.toString(), data.character.id, Transaction(
                                            Date(),
                                            null,
                                            "UPGRADE_BUILDING",
                                            TransactionType.ADD,
                                            mapOf(data.upgradeTo.name to 1f)
                                        )
                                    )

                                mapOf(
                                    "money" to moneyStep,
                                    "materials" to materialStep,
                                    "removal" to removalStep,
                                    "building" to buildingStep,
                                    "transactions" to transactionsStep
                                )
                            }.let {
                                if(it.committed) {
                                    createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(params.locale))
                                } else {
                                    createGenericEmbedError(
                                        it.exception?.stackTraceToString()
                                            ?: CommonLocale.GENERIC_ERROR.locale(params.locale)
                                    )
                                }
                            }.let {
                                interaction.deferPublicResponse().respond(it)
                            }
                        }
                    }
                    else -> interaction.updateWithGenericError(params.locale)
                }

            }
        }

    override suspend fun registerCommand() {
        handleSelection()
        handleUpgrade()
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        val buildings = cacheManager.getCollectionOfType<BuildingWithBounty>(guildId)
        return withOneActiveCharacterOrErrorMessage(responsible, this) { character ->
            val interactionId = compactUuid()
            interactionCache.put(
                interactionId,
                InteractionData(
                    responsible.id,
                    character
                )
            )
            fun InteractionResponseModifyBuilder.() {
                embed {
                    title = BuildingUpgradeLocale.TITLE.locale(locale)
                    description = BuildingUpgradeLocale.SELECT.locale(locale)
                    color = Colors.DEFAULT.value
                }
                character.upgradableBuildings(buildings).chunked(24) { buildingsChunk ->
                    actionRow {
                        stringSelect(buildElementId(BUILDING_TYPE, interactionId)) {
                            buildingsChunk.forEach {
                                option(it.name, it.name)
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        response.respond(builder)
    }

    private fun Character.hasEnoughMaterials(building: BaseBuilding, upgradeBuilding: BaseBuilding) =
        building.upgradeCostItems(upgradeBuilding).entries.all { (material, qty) ->
            inventory[material]?.let { m ->
                m >= qty.proficiencyDiscount(building, this)
            } ?: false
        }

    private fun Character.hasEnoughMoney(building: BaseBuilding, upgradeTo: BaseBuilding) =
        money >= (upgradeTo.moCost - building.moCost)

    private fun Character.doesNotExceedLimits(building: BaseBuilding, serverConfig: ServerConfig) =
        serverConfig.buildingRestrictions.entries.filter{ it.value != null}.all { (restrictionType, limit) ->
            restrictionType.validator(limit!!+1, this, building)
        }

    private fun Character.canUpgrade(building: BaseBuilding, upgradeBuilding: BaseBuilding, serverConfig: ServerConfig) =
        building.upgradeId != null
                && hasEnoughMoney(building, upgradeBuilding)
                && hasEnoughMaterials(building, upgradeBuilding)
                && doesNotExceedLimits(upgradeBuilding, serverConfig)

    private fun Character.buildingWithType(buildingName: String) =
        buildings.entries.flatMap { (type, buildings) ->
            buildings.map { it.name to type.split(":").first() }
        }.first { it.first == buildingName }

    private fun Character.upgradableBuildings(recipes: Collection<BuildingWithBounty>) =
        buildings.flatMap { (compositeId, activeBuildings) ->
            val buildingId = compositeId.split(":").first()
            activeBuildings.filter { it.status == "active" }.takeIf {
                recipes.first { it.name == buildingId }.upgradeId != null
            } ?: emptyList()
        }

    private fun BaseBuilding.upgradeCostItems(upgradeTo: BaseBuilding) =
        upgradeTo.materials.mapValues { (material, qty) ->
            qty - this.materials.getOrDefault(material, 0)
        }
}