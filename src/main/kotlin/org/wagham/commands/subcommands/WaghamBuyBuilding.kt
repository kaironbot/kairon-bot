package org.wagham.commands.subcommands

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.Locale
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.TextInputStyle
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.core.entity.interaction.ModalSubmitInteraction
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.ModalSubmitInteractionCreateEvent
import dev.kord.core.event.interaction.SelectMenuInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.component.option
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.actionRow
import dev.kord.rest.builder.message.modify.embed
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.BuyCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.BuyBuildingLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.Building
import org.wagham.db.models.Character
import org.wagham.db.pipelines.characters.BuildingWithBounty
import org.wagham.utils.createGenericEmbedError
import org.wagham.utils.createGenericEmbedSuccess
import java.util.concurrent.TimeUnit

@BotSubcommand("wagham", BuyCommand::class)
class WaghamBuyBuilding(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "building"
    override val defaultDescription = "Buy a building"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Buy a building",
        Locale.ITALIAN to "Acquista un edificio"
    )
    private val interactionCache: Cache<Snowflake, InteractionData> =
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

    private fun isInteractionActiveAndAccessible(interaction: ComponentInteraction, type: String) =
        interaction.componentId.startsWith("${this@WaghamBuyBuilding::class.qualifiedName}-$type")
                && interactionCache.getIfPresent(interaction.message.id)?.userId == interaction.user.id

    private fun characterCanBuild(character: Character, building: BuildingWithBounty) =
        character.money >= building.moCost && (
                (character.inventory[building.tbadgeType] ?: 0) >= building.tbadgeCost ||
                (
                    building.proficiencyReduction != null &&
                    character.proficiencies.map { it.name }.contains(building.proficiencyReduction) &&
                    (character.inventory[building.tbadgeType] ?: 0) >= building.tbadgeCost/2
                )
        )

    private suspend fun handleSelection() =
        kord.on<SelectMenuInteractionCreateEvent> {
            if(isInteractionActiveAndAccessible(interaction, "building_type")) {
                val updateBehaviour = interaction.deferPublicMessageUpdate()
                val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
                val data = interactionCache.getIfPresent(interaction.message.id) ?: throw IllegalStateException("Cannot find interaction data")
                val buildings = cacheManager.getCollectionOfType<BuildingWithBounty>(data.guildId)
                val building = buildings.firstOrNull { it.name == interaction.values.first() } ?: throw IllegalStateException("Building type does not exist")
                interactionCache.put(interaction.message.id, data.copy(building = building))
                updateBehaviour.edit {
                    embed {
                        title = building.name
                        field {
                            name = BuyBuildingLocale.BUILDING_SIZE.locale(locale)
                            value = building.size
                            inline = false
                        }
                        field {
                            name = BuyBuildingLocale.BUILDING_AREA.locale(locale)
                            value = building.areas.joinToString(", ")
                            inline = false
                        }
                        field {
                            name = BuyBuildingLocale.BUILDING_COST.locale(locale)
                            value = "${building.moCost} MO, ${building.tbadgeCost} ${building.tbadgeType}"
                            inline = false
                        }
                        if(building.proficiencyReduction != null) {
                            field {
                                name =
                                    "${BuyBuildingLocale.BUILDING_COST_WITH_PROFICIENCY.locale(locale)} ${building.proficiencyReduction}"
                                value = "${building.moCost} MO, ${building.tbadgeCost/2} ${building.tbadgeType}"
                                inline = false
                            }
                        }
                        description = buildString {
                            append("**${BuyBuildingLocale.WEEKLY_PRIZE.locale(locale)}**\n")
                            building.bounty.prizes.forEach {
                                append(it.probability*100)
                                append("% - ")
                                if(it.moDelta != 0) {
                                    append(it.moDelta)
                                    append(" MO")
                                }
                                if (it.guaranteedObjectId != null) {
                                    if(it.moDelta != 0) append(", ")
                                    append(it.guaranteedObjectId)
                                    append(" x")
                                    append(it.guaranteedObjectDelta)
                                }
                                it.prizeList.forEach { p ->
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
                    actionRow {
                        stringSelect("${this@WaghamBuyBuilding::class.qualifiedName}-building_type") {
                            buildings.forEach {
                                option(it.name, it.name)
                            }
                        }
                    }
                    actionRow {
                        interactionButton(ButtonStyle.Primary, "${this@WaghamBuyBuilding::class.qualifiedName}-confirm-buy") {
                            label = BuyBuildingLocale.BUILD.locale(locale)
                            disabled = !characterCanBuild(data.character, building)
                        }
                    }
                }
            }
        }

    private suspend fun handleBuy() =
        kord.on<ButtonInteractionCreateEvent> {
            if(isInteractionActiveAndAccessible(interaction, "confirm-buy")) {
                val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
                val data = interactionCache.getIfPresent(interaction.message.id) ?: throw IllegalStateException("Cannot find interaction data")
                if(data.building != null) {
                    interaction.modal(
                        title = "${BuyBuildingLocale.TITLE.locale(locale)} ${data.building.name}",
                        customId = "${this@WaghamBuyBuilding::class.qualifiedName}-modal-buy"
                    ) {
                        actionRow {
                            textInput(
                                TextInputStyle.Short,
                                "${this@WaghamBuyBuilding::class.qualifiedName}-modal-name",
                                BuyBuildingLocale.BUILDING_NAME.locale(locale)
                            ) {
                                allowedLength = 5 .. 60
                                required = true
                            }
                        }
                        actionRow {
                            textInput(
                                TextInputStyle.Paragraph,
                                "${this@WaghamBuyBuilding::class.qualifiedName}-modal-description",
                                BuyBuildingLocale.BUILDING_DESCRIPTION.locale(locale)
                            ) {
                                allowedLength = 10 .. data.building.maxDescriptionSize
                                required = true
                            }
                        }
                    }
                } else {
                    interaction.deferEphemeralResponse().respond(
                        createGenericEmbedError("Something went wrong")
                    )
                }

            }
        }

    private fun isModalActiveAndAccessible(interaction: ModalSubmitInteraction) =
        interaction.modalId.startsWith("${this@WaghamBuyBuilding::class.qualifiedName}-modal-buy")
                && interaction.message != null
                && interactionCache.getIfPresent(interaction.message!!.id)?.userId == interaction.user.id

    private suspend fun handleModal() =
        kord.on<ModalSubmitInteractionCreateEvent> {
            if(isModalActiveAndAccessible(this.interaction)) {
                val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
                val response = interaction.deferEphemeralResponse()
                val data = interactionCache.getIfPresent(interaction.message!!.id)
                    ?: throw IllegalStateException("Cannot find interaction data")
                val buildingName = interaction
                    .textInputs["${this@WaghamBuyBuilding::class.qualifiedName}-modal-name"]
                    ?.value ?: throw IllegalStateException("Cannot find building name")
                val buildingDescription = interaction
                    .textInputs["${this@WaghamBuyBuilding::class.qualifiedName}-modal-description"]
                    ?.value ?: throw IllegalStateException("Cannot find building description")
                val ret = when {
                    data.building == null -> createGenericEmbedError("Building is null")
                    !characterCanBuild(data.character, data.building) -> createGenericEmbedError("Not enough resources")
                    else -> {
                        db.transaction(data.guildId.toString()) {
                            val moneyStep = db.charactersScope.subtractMoney(it, data.guildId.toString(), data.character.name, data.building.moCost.toFloat())
                            val badgeStep = db.charactersScope.removeItemFromInventory(
                                it,
                                data.guildId.toString(),
                                data.character.name,
                                data.building.tbadgeType,
                                data.building.tbadgeCost
                                    .takeIf {
                                        data.building.proficiencyReduction == null ||
                                                !data.character.proficiencies
                                                    .map { it.name }
                                                    .contains(data.building.proficiencyReduction)
                                    } ?: (data.building.tbadgeCost / 2))
                            val building = Building(
                                name = buildingName,
                                description = buildingDescription,
                                zone = data.building.areas.joinToString(" / "),
                                status = "active"
                            )
                            val buildingStep = db.charactersScope.addBuilding(
                                it,
                                data.guildId.toString(),
                                data.character.name,
                                building,
                                data.building.name
                            )
                            moneyStep && badgeStep && buildingStep
                        }.let {
                            if(it.committed) {
                                createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                            } else {
                                createGenericEmbedError(
                                    it.exception?.stackTraceToString()
                                        ?: CommonLocale.GENERIC_ERROR.locale(locale)
                                )
                            }
                        }

                    }
                }
                response.respond(ret)
            }
        }

    override suspend fun registerCommand() {
        handleSelection()
        handleBuy()
        handleModal()
    }


    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val guildId = event.interaction.guildId
        val buildings = cacheManager.getCollectionOfType<BuildingWithBounty>(guildId)
        val ret: InteractionResponseModifyBuilder.() -> Unit = {
            embed {
                title = BuyBuildingLocale.TITLE.locale(locale)
                description = BuyBuildingLocale.SELECT.locale(locale)
            }
            actionRow {
                stringSelect("${this@WaghamBuyBuilding::class.qualifiedName}-building_type") {
                    buildings.forEach {
                        option(it.name, it.name)
                    }
                }
            }
        }
        return ret
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        val guildId = event.interaction.guildId
        val character = db.charactersScope.getActiveCharacter(guildId.toString(), event.interaction.user.id.toString())
        val msg = response.respond(builder)
        interactionCache.put(
            msg.message.id,
            InteractionData(
                event.interaction.user.id,
                guildId,
                character
            )
        )
    }

    private data class InteractionData(
        val userId: Snowflake,
        val guildId: Snowflake,
        val character: Character,
        val building: BuildingWithBounty? = null
    )

}