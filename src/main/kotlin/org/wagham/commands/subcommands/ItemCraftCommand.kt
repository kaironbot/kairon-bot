package org.wagham.commands.subcommands

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.integer
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.ItemCommand
import org.wagham.components.CacheManager
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
import org.wagham.db.models.embed.Transaction
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.*
import java.lang.IllegalStateException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@BotSubcommand("all", ItemCommand::class)
class ItemCraftCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "craft"
    override val defaultDescription = "Craft an item with the current character"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Craft an item with the current character",
        Locale.ITALIAN to "Costruisci un oggetto con il personaggio corrente"
    )
    private val dateFormatter = SimpleDateFormat("dd/MM/YYYY HH:mm:ss")
    private val interactionCache: Cache<Snowflake, Snowflake> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build()

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

    override suspend fun registerCommand() {
        kord.on<ButtonInteractionCreateEvent> {
            val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
            if(interaction.componentId.startsWith("${this@ItemCraftCommand::class.qualifiedName}") && interactionCache.getIfPresent(interaction.message.id) == interaction.user.id) {
                val guildId = interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
                val target = interactionCache.getIfPresent(interaction.message.id) ?: throw IllegalStateException("Cannot find targets")
                val (item, amount) = Regex("${this@ItemCraftCommand::class.qualifiedName}-(.+)-([0-9]+)")
                    .find(interaction.componentId)
                    ?.groupValues
                    ?.let {
                        Pair(it[1], it[2].toInt())
                    } ?: throw IllegalStateException("Cannot parse parameters")
                val items = cacheManager.getCollectionOfType<Item>(guildId)
                checkRequirementsAndCraftItem(guildId, items.first { it.name == item }, amount, target, locale).let {
                    interaction.deferPublicMessageUpdate().edit(it)
                }
            } else if (interaction.componentId.startsWith("${this@ItemCraftCommand::class.qualifiedName}") && interactionCache.getIfPresent(interaction.message.id) == null) {
                interaction.deferEphemeralMessageUpdate().edit(createGenericEmbedError(CommonLocale.INTERACTION_EXPIRED.locale(locale)))
            } else if (interaction.componentId.startsWith("${this@ItemCraftCommand::class.qualifiedName}")) {
                interaction.deferEphemeralResponse().respond(createGenericEmbedError(CommonLocale.INTERACTION_STARTED_BY_OTHER.locale(locale)))
            }
        }
    }

    private suspend fun craftItemAndAssignToCharacter(guildId: String, item: Item, amount: Int, character: String, locale: String) =
        if( (item.craft?.timeRequired ?: 0) > 0) craftItemAndDelayAssignmentToCharacter(guildId, item, amount, character, locale)
        else craftItemAndAssignImmediatelyToCharacter(guildId, item, amount, character, locale)

    private suspend fun craftItemAndAssignImmediatelyToCharacter(guildId: String, item: Item, amount: Int, character: String, locale: String) =
        db.transaction(guildId) { s ->
            val moneyStep = db.charactersScope.subtractMoney(s, guildId, character, item.craft!!.cost*amount)
            val itemsStep = item.craft!!.materials.all { (material, qty) ->
                db.charactersScope.removeItemFromInventory(s, guildId, character, material, qty*amount)
            }
            val assignStep = db.charactersScope.addItemToInventory(s, guildId, character, item.name, amount)

            val itemsRecord = item.craft!!.materials.mapValues { (it.value * amount).toFloat() } +
                    (transactionMoney to item.craft!!.cost*amount)

            val recordStep = db.characterTransactionsScope.addTransactionForCharacter(
                s, guildId, character, Transaction(Date(), null, "CRAFT", TransactionType.REMOVE, itemsRecord)
            ) && db.characterTransactionsScope.addTransactionForCharacter(
                s, guildId, character, Transaction(Date(), null, "CRAFT", TransactionType.ADD, mapOf(item.name to amount.toFloat()))
            )

            moneyStep && itemsStep && assignStep && recordStep
        }.let {
            when {
                it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
            }
        }

    private suspend fun craftItemAndDelayAssignmentToCharacter(guildId: String, item: Item, amount: Int, character: String, locale: String) =
        db.transaction(guildId) { s ->
            val moneyStep = db.charactersScope.subtractMoney(s, guildId, character, item.craft!!.cost*amount)
            val itemsStep = item.craft!!.materials.all { (material, qty) ->
                db.charactersScope.removeItemFromInventory(s, guildId, character, material, qty*amount)
            }

            val itemsRecord = item.craft!!.materials.mapValues { (it.value * amount).toFloat() } +
                    (transactionMoney to item.craft!!.cost*amount)

            val recordStep = db.characterTransactionsScope.addTransactionForCharacter(
                s, guildId, character, Transaction(Date(), null, "CRAFT", TransactionType.REMOVE, itemsRecord)
            )

            val task = ScheduledEvent(
                uuid(),
                ScheduledEventType.GIVE_ITEM,
                Date(),
                Date(System.currentTimeMillis() + item.craft!!.timeRequired!!),
                ScheduledEventState.SCHEDULED,
                mapOf(
                    ScheduledEventArg.ITEM to item.name,
                    ScheduledEventArg.INT_QUANTITY to "$amount",
                    ScheduledEventArg.TARGET to character
                )
            )

            cacheManager.scheduleEvent(Snowflake(guildId), task)

            moneyStep && itemsStep && recordStep
        }.let {
            when {
                it.committed -> createGenericEmbedSuccess(
                    "${ItemCraftLocale.READY_ON.locale(locale)}: ${dateFormatter.format(Date(System.currentTimeMillis() + item.craft!!.timeRequired!!))}"
                )
                it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
            }
        }

    private fun Character.buildingRequirement(item: Item) =
        item.craft?.buildings.isNullOrEmpty() ||
            buildings.keys.map { it.split(":").first() }.any {
                item.craft!!.buildings.contains(it)
            }

    private fun Character.proficiencyRequirement(item: Item) =
        item.craft?.tools.isNullOrEmpty() ||
                proficiencies.map { it.name }.any {
                    item.craft!!.tools.contains(it)
                }

    private fun Character.missingMaterials(item: Item, amount: Int) =
        item.craft?.materials?.mapValues {
            (it.value * amount) - inventory.getOrDefault(it.key, 0)
        }?.filterValues {
            it > 0
        } ?: emptyMap()

    private suspend fun checkRequirementsAndCraftItem(guildId: String, item: Item, amount: Int, player: Snowflake, locale: String): InteractionResponseModifyBuilder.() -> Unit {
        val character = db.charactersScope.getActiveCharacter(guildId, player.toString())
        return when {
            item.craft == null -> createGenericEmbedError(ItemCraftLocale.CANNOT_CRAFT.locale(locale))
            item.craft?.minQuantity != null && amount < item.craft!!.minQuantity!! ->
                createGenericEmbedError("${ItemCraftLocale.NOT_ENOUGH.locale(locale)} ${item.craft?.minQuantity}")
            item.craft?.maxQuantity != null && amount > item.craft!!.maxQuantity!! ->
                createGenericEmbedError("${ItemCraftLocale.TOO_MUCH.locale(locale)} ${item.craft?.maxQuantity}")
            !character.buildingRequirement(item) -> createGenericEmbedError(
                ItemCraftLocale.BUILDINGS_REQUIRED.locale(locale) + ": " + item.craft!!.buildings.joinToString(", ")
            )
            !character.proficiencyRequirement(item) -> createGenericEmbedError(
                ItemCraftLocale.TOOLS_REQUIRED.locale(locale) + ": " + item.craft!!.tools.joinToString(", ")
            )
            character.missingMaterials(item, amount).isNotEmpty() -> createGenericEmbedError(
                ItemCraftLocale.MISSING_MATERIALS.locale(locale) + ": " + character.missingMaterials(item, amount).entries.joinToString(", ") {
                    "${it.key} x${it.value}"
                }
            )
            character.money < (item.craft!!.cost * amount) -> createGenericEmbedError(CommonLocale.NOT_ENOUGH_MONEY.locale(locale))
            else -> craftItemAndAssignToCharacter(guildId, item, amount, character.id, locale)
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val items = cacheManager.getCollectionOfType<Item>(guildId)
        val amount = event.interaction.command.integers["amount"]?.toInt()?.takeIf { it > 0 }
            ?: throw IllegalStateException(ItemCraftLocale.INVALID_QTY.locale(locale))
        val item = event.interaction.command.strings["item"] ?: throw IllegalStateException("Item not provided")
        val target = event.interaction.user.id
        return try {
            if (items.firstOrNull { it.name == item } == null) {
                val probableItem = items.maxByOrNull { item.levenshteinDistance(it.name) }
                alternativeOptionMessage(locale, item, probableItem?.name, "${this@ItemCraftCommand::class.qualifiedName}-${probableItem?.name}-$amount")
            } else {
                checkRequirementsAndCraftItem(guildId, items.first { it.name == item }, amount, target, locale)
            }
        } catch (e: NoActiveCharacterException) {
            createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
        }
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        val msg = response.respond(builder)
        interactionCache.put(
            msg.message.id,
            event.interaction.user.id
        )
    }

}