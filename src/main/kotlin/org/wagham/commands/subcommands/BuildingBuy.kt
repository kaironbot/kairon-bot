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
import dev.kord.rest.builder.message.actionRow
import dev.kord.rest.builder.message.embed
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
import org.wagham.utils.*
import java.util.*
import java.util.concurrent.TimeUnit

@BotSubcommand("all", BuildingCommand::class)
class BuildingBuy(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    companion object {
        private const val buildingType = "building_type"
        private const val confirm = "confirm_buy"
        private const val modalId = "modal_buy"
        private const val modalName = "modal_name"
        private const val modalDescription = "modal_description"
        private data class InteractionData(
            val userId: Snowflake,
            val character: Character,
            val building: BuildingWithBounty? = null
        )
    }

    override val commandName = "buy"
    override val defaultDescription = BuildingBuyLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = BuildingBuyLocale.DESCRIPTION.localeMap
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
            if(verifyId(interaction.componentId, buildingType)) {
                val params = interaction.extractCommonParameters()
                val (_, id) = extractComponentsFromComponentId(interaction.componentId)
                val data = interactionCache.getIfPresent(id)
                val buildings = getEligibleBuildings(params.guildId)
                val config = cacheManager.getConfig(params.guildId)
                val building = buildings.first { it.name == interaction.values.first() }
                when {
                    data == null -> interaction.updateWithExpirationError(params.locale)
                    data.userId != params.responsible.id -> interaction.respondWithForbiddenError(params.locale)
                    else -> {
                        interactionCache.put(id, data.copy(building = building))
                        interaction.deferPublicMessageUpdate().edit {
                            embed(BuildingCommand.describeBuildingMessage(building, params.locale, config, data.character))
                            buildings.chunked(24) { buildingsChunk ->
                                actionRow {
                                    stringSelect(buildElementId(buildingType, id)) {
                                        buildingsChunk.forEach {
                                            option(it.name, it.name)
                                        }
                                    }
                                }
                            }
                            actionRow {
                                interactionButton(ButtonStyle.Primary, buildElementId(confirm, id)) {
                                    label = BuildingBuyLocale.BUILD.locale(params.locale)
                                    disabled = !data.character.canBuild(building, config)
                                }
                            }
                        }

                    }
                }
            }
        }

    private suspend fun handleBuy() =
        kord.on<ButtonInteractionCreateEvent> {
            if(verifyId(interaction.componentId, confirm)) {
                val params = interaction.extractCommonParameters()
                val (_, id) = extractComponentsFromComponentId(interaction.componentId)
                val data = interactionCache.getIfPresent(id)
                when {
                    data == null -> interaction.updateWithExpirationError(params.locale)
                    data.userId != params.responsible.id -> interaction.respondWithForbiddenError(params.locale)
                    data.building != null -> {
                        interaction.modal(
                            title = "${BuildingBuyLocale.TITLE.locale(params.locale)} ${data.building.name}",
                            customId = buildElementId(modalId, id)
                        ) {
                            actionRow {
                                textInput(
                                    TextInputStyle.Short,
                                    buildElementId(modalName),
                                    BuildingBuyLocale.BUILDING_NAME.locale(params.locale)
                                ) {
                                    allowedLength = 5 .. 60
                                    required = true
                                }
                            }
                            actionRow {
                                textInput(
                                    TextInputStyle.Paragraph,
                                    buildElementId(modalDescription),
                                    BuildingBuyLocale.BUILDING_DESCRIPTION.locale(params.locale)
                                ) {
                                    allowedLength = 10 .. data.building.maxDescriptionSize
                                    required = true
                                }
                            }
                        }
                    }
                    else -> interaction.updateWithGenericError(params.locale)
                }
            }
        }


    private suspend fun handleModal() =
        kord.on<ModalSubmitInteractionCreateEvent> {
            if(verifyId(interaction.modalId, modalId)) {
                val params = interaction.extractCommonParameters()
                val (_, id) = extractComponentsFromComponentId(interaction.modalId)
                val data = interactionCache.getIfPresent(id)
                val config = cacheManager.getConfig(params.guildId)
                when {
                    data == null -> interaction.updateWithExpirationError(params.locale)
                    data.userId != params.responsible.id -> interaction.respondWithForbiddenError(params.locale)
                    data.building != null -> {
                        val buildingName = interaction.extractInput(modalName)
                        val buildingDescription = interaction.extractInput(modalDescription)
                        when {
                            !data.character.canBuild(data.building, config) -> createGenericEmbedError("Not enough resources")
                            else -> {
                                db.transaction(params.guildId.toString()) { session ->
                                    val moneyStep = db.charactersScope.subtractMoney(session, params.guildId.toString(), data.character.id, data.building.moCost.toFloat())
                                    val materialStep = data.building.materials.entries.all { (material, qty) ->
                                        db.charactersScope.removeItemFromInventory(
                                            session,
                                            params.guildId.toString(),
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
                                        params.guildId.toString(),
                                        data.character.id,
                                        building,
                                        data.building
                                    )

                                    val ingredients = data.building.materials
                                        .mapValues { it.value.proficiencyDiscount(data.building, data.character).toFloat() } +
                                            (transactionMoney to data.building.moCost.toFloat())

                                    val transactionsStep =
                                        db.characterTransactionsScope.addTransactionForCharacter(
                                            session, params.guildId.toString(), data.character.id, Transaction(
                                                Date(),
                                                null,
                                                "BUY_BUILDING",
                                                TransactionType.REMOVE,
                                                ingredients
                                            )
                                        ) && db.characterTransactionsScope.addTransactionForCharacter(
                                            session, params.guildId.toString(), data.character.id, Transaction(
                                                Date(),
                                                null,
                                                "BUY_BUILDING",
                                                TransactionType.ADD,
                                                mapOf(data.building.name to 1f)
                                            )
                                        )

                                    mapOf(
                                        "money" to moneyStep,
                                        "material" to materialStep,
                                        "build" to buildingStep,
                                        "transaction" to transactionsStep
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
                                }

                            }
                        }.let {
                            interaction.deferPublicResponse().respond(it)
                        }
                    }
                }
            }
        }

    override suspend fun registerCommand() {
        handleSelection()
        handleBuy()
        handleModal()
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        withOneActiveCharacterOrErrorMessage(responsible, this) { character ->
            val interactionId = compactUuid()
            interactionCache.put(
                interactionId,
                InteractionData(
                    event.interaction.user.id,
                    character
                )
            )
            val buildings = getEligibleBuildings(guildId)
            fun InteractionResponseModifyBuilder.() {
                embed {
                    title = BuildingBuyLocale.TITLE.locale(locale)
                    description = BuildingBuyLocale.SELECT.locale(locale)
                    color = Colors.DEFAULT.value
                }
                buildings.chunked(24) { buildingsChunk ->
                    actionRow {
                        stringSelect(buildElementId(buildingType, interactionId)) {
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
        event.interaction.deferPublicResponse().respond(builder)
    }

    private suspend fun getEligibleBuildings(guildId: Snowflake) =
        cacheManager.getCollectionOfType<BuildingWithBounty>(guildId).filter { !it.upgradeOnly }

    private fun ModalSubmitInteraction.extractInput(id: String) =
        textInputs[buildElementId(id)]?.value?.trim() ?: throw IllegalStateException("Cannot find $id")
}