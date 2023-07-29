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
import kotlinx.coroutines.flow.first
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.BuildingCommand
import org.wagham.commands.impl.BuildingCommand.Companion.proficiencyDiscount
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.BuildingBuyLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.TransactionType
import org.wagham.db.models.Building
import org.wagham.db.models.Character
import org.wagham.db.models.ServerConfig
import org.wagham.db.models.embed.Transaction
import org.wagham.db.pipelines.buildings.BuildingWithBounty
import org.wagham.utils.createGenericEmbedError
import org.wagham.utils.createGenericEmbedSuccess
import org.wagham.utils.transactionMoney
import java.util.*
import java.util.concurrent.TimeUnit

@BotSubcommand("all", BuildingCommand::class)
class BuildingBuy(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "buy"
    override val defaultDescription = "Buy a building"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to defaultDescription,
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
        interaction.componentId.startsWith("${this@BuildingBuy::class.qualifiedName}-$type")
                && interactionCache.getIfPresent(interaction.message.id)?.userId == interaction.user.id

    private fun Character.hasResources(building: BuildingWithBounty) =
        money >= building.moCost
            && building.materials.entries.all { (material, qty) ->
                inventory[material]?.let { m ->
                    m >= qty.proficiencyDiscount(building, this)
                } ?: false
        }

    private fun Character.doesNotExceedLimits(building: BuildingWithBounty, serverConfig: ServerConfig) =
        serverConfig.buildingRestrictions.entries.filter{ it.value != null}.all { (restrictionType, limit) ->
            restrictionType.validator(limit!!, this, building)
        }

    private fun Character.canBuild(building: BuildingWithBounty, serverConfig: ServerConfig) =
        !building.upgradeOnly && hasResources(building) && doesNotExceedLimits(building, serverConfig)

    private suspend fun handleSelection() =
        kord.on<SelectMenuInteractionCreateEvent> {
            if(isInteractionActiveAndAccessible(interaction, "building_type")) {
                val updateBehaviour = interaction.deferPublicMessageUpdate()
                val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
                val data = interactionCache.getIfPresent(interaction.message.id) ?: throw IllegalStateException("Cannot find interaction data")
                val buildings = getEligibleBuildings(data.guildId)
                val config = cacheManager.getConfig(data.guildId)
                val building = buildings.firstOrNull { it.name == interaction.values.first() } ?: throw IllegalStateException("Building type does not exist")
                interactionCache.put(interaction.message.id, data.copy(building = building))
                updateBehaviour.edit {
                    embed(BuildingCommand.describeBuildingMessage(building, locale, config, data.character))
                    buildings.chunked(24) { buildingsChunk ->
                        actionRow {
                            stringSelect("${this@BuildingBuy::class.qualifiedName}-building_type") {
                                buildingsChunk.forEach {
                                    option(it.name, it.name)
                                }
                            }
                        }
                    }
                    actionRow {
                        interactionButton(ButtonStyle.Primary, "${this@BuildingBuy::class.qualifiedName}-confirm-buy") {
                            label = BuildingBuyLocale.BUILD.locale(locale)
                            disabled = !data.character.canBuild(building, config)
                        }
                    }
                }
            }
        }

    private suspend fun getEligibleBuildings(guildId: Snowflake) =
        cacheManager.getCollectionOfType<BuildingWithBounty>(guildId).filter { !it.upgradeOnly }

    private suspend fun handleBuy() =
        kord.on<ButtonInteractionCreateEvent> {
            if(isInteractionActiveAndAccessible(interaction, "confirm-buy")) {
                val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
                val data = interactionCache.getIfPresent(interaction.message.id) ?: throw IllegalStateException("Cannot find interaction data")
                if(data.building != null) {
                    interaction.modal(
                        title = "${BuildingBuyLocale.TITLE.locale(locale)} ${data.building.name}",
                        customId = "${this@BuildingBuy::class.qualifiedName}-modal-buy"
                    ) {
                        actionRow {
                            textInput(
                                TextInputStyle.Short,
                                "${this@BuildingBuy::class.qualifiedName}-modal-name",
                                BuildingBuyLocale.BUILDING_NAME.locale(locale)
                            ) {
                                allowedLength = 5 .. 60
                                required = true
                            }
                        }
                        actionRow {
                            textInput(
                                TextInputStyle.Paragraph,
                                "${this@BuildingBuy::class.qualifiedName}-modal-description",
                                BuildingBuyLocale.BUILDING_DESCRIPTION.locale(locale)
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
        interaction.modalId.startsWith("${this@BuildingBuy::class.qualifiedName}-modal-buy")
                && interaction.message != null
                && interactionCache.getIfPresent(interaction.message!!.id)?.userId == interaction.user.id

    private suspend fun handleModal() =
        kord.on<ModalSubmitInteractionCreateEvent> {
            if(isModalActiveAndAccessible(this.interaction)) {
                val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
                val response = interaction.deferEphemeralResponse()
                val data = interactionCache.getIfPresent(interaction.message!!.id)
                    ?: throw IllegalStateException("Cannot find interaction data")
                val config = cacheManager.getConfig(data.guildId)
                val buildingName = interaction
                    .textInputs["${this@BuildingBuy::class.qualifiedName}-modal-name"]
                    ?.value?.trim() ?: throw IllegalStateException("Cannot find building name")
                val buildingDescription = interaction
                    .textInputs["${this@BuildingBuy::class.qualifiedName}-modal-description"]
                    ?.value ?: throw IllegalStateException("Cannot find building description")
                val ret = when {
                    data.building == null -> createGenericEmbedError("Building is null")
                    !data.character.canBuild(data.building, config) -> createGenericEmbedError("Not enough resources")
                    else -> {
                        db.transaction(data.guildId.toString()) { session ->
                            val moneyStep = db.charactersScope.subtractMoney(session, data.guildId.toString(), data.character.id, data.building.moCost.toFloat())
                            val materialStep = data.building.materials.entries.all { (material, qty) ->
                                db.charactersScope.removeItemFromInventory(
                                    session,
                                    data.guildId.toString(),
                                    data.character.id,
                                    material,
                                    qty.proficiencyDiscount(data.building, data.character))
                            }

                            val building = Building(
                                name = buildingName,
                                description = buildingDescription,
                                zone = data.building.areas.joinToString(" / "),
                                status = "active"
                            )
                            val buildingStep = db.charactersScope.addBuilding(
                                session,
                                data.guildId.toString(),
                                data.character.id,
                                building,
                                data.building
                            )

                            val ingredients = data.building.materials
                                .mapValues { it.value.proficiencyDiscount(data.building, data.character).toFloat() } +
                                    (transactionMoney to data.building.moCost.toFloat())

                            val transactionsStep =
                                db.characterTransactionsScope.addTransactionForCharacter(
                                    session, data.guildId.toString(), data.character.id, Transaction(
                                        Date(),
                                        null,
                                        "BUY_BUILDING",
                                        TransactionType.REMOVE,
                                        ingredients
                                    )
                                ) && db.characterTransactionsScope.addTransactionForCharacter(
                                    session, data.guildId.toString(), data.character.id, Transaction(
                                        Date(),
                                        null,
                                        "BUY_BUILDING",
                                        TransactionType.ADD,
                                        mapOf(data.building.name to 1f)
                                    )
                                )

                            moneyStep && materialStep && buildingStep && transactionsStep
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
        val buildings = getEligibleBuildings(event.interaction.guildId)
        val ret: InteractionResponseModifyBuilder.() -> Unit = {
            embed {
                title = BuildingBuyLocale.TITLE.locale(locale)
                description = BuildingBuyLocale.SELECT.locale(locale)
                color = Colors.DEFAULT.value
            }
            buildings.chunked(24) { buildingsChunk ->
                actionRow {
                    stringSelect("${this@BuildingBuy::class.qualifiedName}-building_type") {
                        buildingsChunk.forEach {
                            option(it.name, it.name)
                        }
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
        val guildId = event.interaction.guildId
        //TODO fix this
        val character = db.charactersScope.getActiveCharacters(guildId.toString(), event.interaction.user.id.toString()).first()
        val msg = event.interaction.deferPublicResponse().respond(builder)
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