package org.wagham.commands.subcommands

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.mongodb.reactivestreams.client.ClientSession
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
import dev.kord.rest.builder.interaction.integer
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.actionRow
import dev.kord.rest.builder.message.modify.embed
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.ItemCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.ItemCraftLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.ScheduledEventArg
import org.wagham.db.enums.ScheduledEventState
import org.wagham.db.enums.ScheduledEventType
import org.wagham.db.enums.TransactionType
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Character
import org.wagham.db.models.Item
import org.wagham.db.models.ScheduledEvent
import org.wagham.db.models.embed.CraftRequirement
import org.wagham.db.models.embed.Transaction
import org.wagham.utils.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.IllegalStateException

@BotSubcommand("all", ItemCommand::class)
class ItemCraft(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "craft"
    override val defaultDescription = ItemCraftLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = ItemCraftLocale.DESCRIPTION.localeMap
    private val dateFormatter = SimpleDateFormat("dd/MM/YYYY HH:mm:ss")
    private val interactionCache: Cache<String, CraftItemInteractionData> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build()

    companion object {
        private const val ABORT = "recipeAbort"
        private const val ALTERNATIVE = "itemAlternative"
        private const val CONFIRM = "recipeConfirm"
        private const val RECIPE_SELECTION = "recipeAlternative"

        private data class CraftItemInteractionData(
            val responsible: Snowflake,
            val target: Character,
            val item: Item,
            val amount: Int,
            val recipeIndex: Int = 0
        )

    }

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("item", ItemCraftLocale.ITEM.locale("en")) {
            ItemCraftLocale.ITEM.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
        integer("amount", ItemCraftLocale.AMOUNT.locale("en")) {
            ItemCraftLocale.AMOUNT.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
    }

    private suspend fun handleButtons() = kord.on<ButtonInteractionCreateEvent> {
        if(verifyId(interaction.componentId)) {
            val params = interaction.extractCommonParameters()
            val (id, operation) = extractComponentsFromComponentId(interaction.componentId)
            val data = interactionCache.getIfPresent(id)
            when {
                operation == ABORT -> interaction.operationCanceled(params.locale)
                data == null -> interaction.updateWithExpirationError(params.locale)
                data.responsible != params.responsible.id -> interaction.respondWithForbiddenError(params.locale)
                operation == ALTERNATIVE -> {
                    checkRequirementsAndCraftItem(params.guildId.toString(), params.responsible.id, data.item, data.amount, data.target, params.locale).let {
                        interaction.deferPublicMessageUpdate().edit(it)
                    }
                }
                operation == CONFIRM -> {
                    craftItemAndAssignToCharacter(params.guildId.toString(), data.item, data.recipeIndex, data.amount, data.target.id, params.locale).let {
                        interaction.deferPublicMessageUpdate().edit(it)
                    }
                }
            }
        }
    }

    private fun handleSelection() = kord.on<SelectMenuInteractionCreateEvent> {
        if(verifyId(interaction.componentId)) {
            val params = interaction.extractCommonParameters()
            val (id, _) = extractComponentsFromComponentId(interaction.componentId)
            val data = interactionCache.getIfPresent(id)
            if(data == null) {
                interaction.updateWithExpirationError(params.locale)
            } else {
                interactionCache.put(
                    id,
                    data.copy(recipeIndex = interaction.values.first().toInt())
                )
                interaction.deferPublicMessageUpdate()
            }
        }
    }

    override suspend fun registerCommand() {
        handleButtons()
        handleSelection()
    }

    private suspend fun craftItemAndAssignToCharacter(guildId: String, item: Item, recipeIndex: Int, amount: Int, character: String, locale: String) =
        if( (item.craft[recipeIndex].timeRequired ?: 0) > 0) craftItemAndDelayAssignmentToCharacter(guildId, item, recipeIndex, amount, character, locale)
        else craftItemAndAssignImmediatelyToCharacter(guildId, item, recipeIndex,amount, character, locale)

    private suspend fun payCostAndRecord(
        s: ClientSession,
        guildId: String,
        character: String,
        recipe: CraftRequirement,
        amount: Int) : Boolean {
        val moneyStep = db.charactersScope.subtractMoney(s, guildId, character, recipe.cost * amount)
        val itemsStep = recipe.materials.all { (material, qty) ->
            db.charactersScope.removeItemFromInventory(s, guildId, character, material, (qty*amount).toInt())
        }
        val itemsRecord = recipe.materials.mapValues { (it.value * amount) } +
                (transactionMoney to recipe.cost*amount)
        val recordStep = db.characterTransactionsScope.addTransactionForCharacter(
            s, guildId, character, Transaction(Date(), null, "CRAFT", TransactionType.REMOVE, itemsRecord)
        )
        return moneyStep && itemsStep && recordStep
    }

    private suspend fun craftItemAndAssignImmediatelyToCharacter(guildId: String, item: Item, recipeIndex: Int, amount: Int, character: String, locale: String) =
        db.transaction(guildId) { s ->
            val recipe = item.craft[recipeIndex]
            val costStep = payCostAndRecord(s, guildId, character, recipe, amount)

            val assignStep = db.charactersScope.addItemToInventory(s, guildId, character, item.name, amount)

            val recordStep = costStep && db.characterTransactionsScope.addTransactionForCharacter(
                s, guildId, character, Transaction(Date(), null, "CRAFT", TransactionType.ADD, mapOf(item.name to amount.toFloat()))
            )

            assignStep && recordStep
        }.let {
            when {
                it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
            }
        }

    private suspend fun craftItemAndDelayAssignmentToCharacter(guildId: String, item: Item, recipeIndex: Int, amount: Int, character: String, locale: String): InteractionResponseModifyBuilder.() -> Unit {
        val recipe = item.craft[recipeIndex]
        return db.transaction(guildId) { s ->
            val result = payCostAndRecord(s, guildId, character, recipe, amount)
            result
        }.let {
            when {
                it.committed -> {
                    val task = ScheduledEvent(
                        uuid(),
                        ScheduledEventType.GIVE_ITEM,
                        Date(),
                        Date(System.currentTimeMillis() + recipe.timeRequired!!),
                        ScheduledEventState.SCHEDULED,
                        mapOf(
                            ScheduledEventArg.ITEM to item.name,
                            ScheduledEventArg.INT_QUANTITY to "$amount",
                            ScheduledEventArg.TARGET to character
                        )
                    )

                    cacheManager.scheduleEvent(Snowflake(guildId), task)
                    createGenericEmbedSuccess(
                        "${ItemCraftLocale.READY_ON.locale(locale)}: ${dateFormatter.format(Date(System.currentTimeMillis() + item.craft[recipeIndex].timeRequired!!))}"
                    )
                }
                it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
            }
        }
    }

    private fun generateRecipeDisambiguationMessage(
        item: Item,
        recipeIndexes: List<Int>,
        interactionId: String,
        locale: String
    ): InteractionResponseModifyBuilder.() -> Unit = {
        embed {
            title = ItemCraftLocale.ALTERNATIVE_RECIPES.locale(locale)
            description = buildString {
                append(ItemCraftLocale.ALTERNATIVE_RECIPES_SELECT.locale(locale))
                append("\n")
                recipeIndexes.forEach {
                    append("**${
                        item.craft[it].label ?: (ItemCraftLocale.RECIPE.locale(locale) + " $it")
                    }**: ")
                    append(item.craft[it].summary())
                    append("\n")
                }
            }
            color = Colors.DEFAULT.value
        }

        actionRow {
            stringSelect(buildElementId(interactionId, RECIPE_SELECTION)) {
                recipeIndexes.forEachIndexed { index, it ->
                    val label = item.craft[it].label ?: (ItemCraftLocale.RECIPE.locale(locale) + " $it")
                    option(label, "$it") {
                        default = index == 0
                    }
                }
            }
        }
        actionRow {
            interactionButton(ButtonStyle.Primary, buildElementId(interactionId, CONFIRM)) {
                label = CommonLocale.CONFIRM.locale(locale)
            }
            interactionButton(ButtonStyle.Danger, buildElementId(interactionId, ABORT)) {
                label = CommonLocale.ABORT.locale(locale)
            }
        }
    }

    private suspend fun checkRequirementsAndCraftItem(guildId: String, responsible: Snowflake, item: Item, amount: Int, character: Character, locale: String): InteractionResponseModifyBuilder.() -> Unit {
        val recipeIndexes = item.selectCraftOptions(character, amount)
        return when {
            item.craft.isEmpty() -> createGenericEmbedError(ItemCraftLocale.NOT_CRAFTABLE.locale(locale))
            recipeIndexes.isEmpty() -> createGenericEmbedError(ItemCraftLocale.NO_RECIPE_AVAILABLE.locale(locale))
            recipeIndexes.size == 1 -> craftItemAndAssignToCharacter(guildId, item, recipeIndexes.first(), amount, character.id, locale)
            else -> {
                val interactionId = compactUuid().substring(0, 6)
                interactionCache.put(
                    interactionId,
                    CraftItemInteractionData(responsible, character, item, amount)
                )
                generateRecipeDisambiguationMessage(item, recipeIndexes, interactionId, locale)
            }
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        val items = cacheManager.getCollectionOfType<Item>(guildId)
        val amount = event.interaction.command.integers["amount"]?.toInt()?.takeIf { it > 0 }
            ?: throw IllegalStateException(ItemCraftLocale.INVALID_QTY.locale(locale))
        val item = event.interaction.command.strings["item"] ?: throw IllegalStateException("Item not provided")
        withOneActiveCharacterOrErrorMessage(responsible, this) { character ->
            items.firstOrNull { it.name == item }?.let {
                checkRequirementsAndCraftItem(guildId.toString(), responsible.id, it, amount, character, locale)
            } ?: items.maxByOrNull { item.levenshteinDistance(it.name) }?.let { probableItem ->
                val interactionId = compactUuid().substring(0, 6)
                interactionCache.put(
                    interactionId,
                    CraftItemInteractionData(responsible.id, character, probableItem, amount)
                )
                alternativeOptionMessage(locale, item, probableItem.name, buildElementId(interactionId, ALTERNATIVE))
            } ?: createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))
        }
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        response.respond(builder)
    }

}