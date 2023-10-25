package org.wagham.events

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.inmo.krontab.doInfinity
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.SelectMenuInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.component.option
import dev.kord.rest.builder.message.create.UserMessageCreateBuilder
import dev.kord.rest.builder.message.create.actionRow
import dev.kord.rest.builder.message.create.embed
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import org.wagham.annotations.BotEvent
import org.wagham.components.CacheManager
import org.wagham.components.Identifiable
import org.wagham.config.Channels
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.events.WeeklyMarketLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.TransactionType
import org.wagham.db.models.Item
import org.wagham.db.models.WeeklyMarket
import org.wagham.db.models.embed.CraftRequirement
import org.wagham.db.models.embed.LabelStub
import org.wagham.db.models.embed.Transaction
import org.wagham.db.utils.ItemId
import org.wagham.utils.*
import java.util.*
import java.util.concurrent.TimeUnit

@BotEvent("wagham")
class PeriodicMarketEvent(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : Event, Identifiable {

    override val eventId = "periodic_market_event"

    private val taskExecutorScope = CoroutineScope(Dispatchers.Default)
    private val logger = KotlinLogging.logger {}
    private val mutex = Mutex()
    private val marketCache: Cache<Snowflake, WeeklyMarket> = Caffeine.newBuilder().build()
    private val interactionCache: Cache<String, String> = Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build()
    private val categories = mapOf<LabelStub, Int>(LabelStub("a", "B") to 10) // TODO

    private suspend fun retrieveItemsForCategory(guildId: Snowflake, type: LabelStub, qty: Int) =
        //db.itemsScope.getItems(guildId.toString(), listOf(type)).toFlow()
         db.itemsScope.getAllItems(guildId.toString()).filter {
             it.craft.isNotEmpty()
         }.take(100)
        .toList().shuffled().take(qty).associateWith { it.craft.first() }

    /**
     * Handles the selection of an item by putting the ID of the corresponding item in the interaction cache.
     */
    private fun handleSelection() = kord.on<SelectMenuInteractionCreateEvent> {
        if (verifyId(interaction.componentId)) {
            val params = interaction.extractCommonParameters()
            interactionCache.put("${params.guildId}:${params.responsible.id}", interaction.values.first())
            interaction.deferPublicMessageUpdate()
        }
    }

    private suspend fun updateMarket(guildId: Snowflake, characterId: String, itemId: String) = mutex.withLock {
        val currentMarket = marketCache.getIfPresent(guildId)
            ?: db.utilityScope.getLastMarket(guildId.toString())
            ?: throw IllegalStateException("Market not found")
        currentMarket.copy(
            buyLog = currentMarket.buyLog + (
                characterId to (currentMarket.buyLog.getOrDefault(characterId, emptyList()) + itemId)
            )
        ).also { marketCache.put(guildId, it) }

    }

    private fun handleButton() = kord.on<ButtonInteractionCreateEvent> {
        if (verifyId(interaction.componentId)) {
            val params = interaction.extractCommonParameters()
            val playerId = params.responsible.id.toString()
            val itemId = interactionCache.getIfPresent("${params.guildId}:${playerId}")
            val market = marketCache.getIfPresent(params.guildId) ?: db.utilityScope.getLastMarket(params.guildId.toString())
            val characters = db.charactersScope.getActiveCharacterOrAllActive(
                params.guildId.toString(),
                params.responsible.id.toString()
            )
            val activeCharacter = characters.currentActive
            when {
                activeCharacter == null && characters.allActive.isEmpty() ->
                    interaction.respondWithCustomError(
                        "<@!${params.responsible.id}> ${CommonLocale.NO_ACTIVE_CHARACTER.locale(params.locale)}"
                    )
                activeCharacter == null -> interaction.respondWithCustomError(CommonLocale.MULTIPLE_CHARACTERS.locale(params.locale))
                itemId == null -> interaction.respondWithExpirationError(params.locale)
                market == null -> interaction.respondWithCustomError(WeeklyMarketLocale.NO_MARKET.locale(params.locale))
                market.idToItems[itemId] == null ->
                    interaction.respondWithCustomError(WeeklyMarketLocale.NO_ITEM.locale(params.locale))
                market.buyLog[activeCharacter.id]?.contains(itemId) == true ->
                    interaction.respondWithCustomError(WeeklyMarketLocale.ALREADY_BOUGHT.locale(params.locale))
                activeCharacter.money < market.items.getValue(market.idToItems.getValue(itemId)).cost ->
                    interaction.respondWithCustomError(CommonLocale.NOT_ENOUGH_MONEY.locale(params.locale))
                else -> {
                    val item = market.idToItems.getValue(itemId)
                    val cost = market.items.getValue(item).cost
                    db.transaction(params.guildId.toString()) { s ->
                        db.charactersScope.subtractMoney(s, params.guildId.toString(), activeCharacter.id, cost) &&
                            db.charactersScope.addItemToInventory(s, params.guildId.toString(), activeCharacter.id, item, 1) &&
                            db.characterTransactionsScope.addTransactionForCharacter(
                                s, params.guildId.toString(), activeCharacter.id, Transaction(Date(), null, "BUY", TransactionType.REMOVE, mapOf(transactionMoney to cost))
                            ) &&
                            db.characterTransactionsScope.addTransactionForCharacter(
                                s, params.guildId.toString(), activeCharacter.id, Transaction(Date(), null, "BUY", TransactionType.ADD, mapOf(item to 1.0f))
                            )
                    }.let {
                        if(it.committed) {
                            val newMarket = updateMarket(params.guildId, activeCharacter.id, itemId)
                            interaction.respondWithGenericSuccess(params.locale)
                            db.utilityScope.updateMarket(params.guildId.toString(), newMarket)
                        } else {
                            interaction.respondWithCustomError("Something went wrong:\n${it.exception?.stackTraceToString()}")
                        }
                    }
                }
            }
        }
    }

    /**
     * Invalidates the previous market message by removing the controls on the discord message and the elements from
     * the local caches.
     *
     * @param guildId the ID of the guild where to invalidate the market.
     */
    private suspend fun disablePreviousMarket(guildId: Snowflake) {
        db.utilityScope.getLastMarket(guildId.toString())?.let { lastMarket ->
            val channel = kord.getChannelOfType(guildId, Channels.MARKET_CHANNEL, cacheManager)
            channel.getMessage(Snowflake(lastMarket.message)).edit {
                components = mutableListOf()
            }
        }
        marketCache.invalidate(guildId)
    }

    private suspend fun Map<Item, CraftRequirement>.generateMessage(guildId: Snowflake, idMap: Map<String, ItemId>): UserMessageCreateBuilder.() -> Unit {
        val locale = kord.defaultSupplier.getGuild(guildId).preferredLocale.language
        return fun UserMessageCreateBuilder.() {
            embed {
                title = WeeklyMarketLocale.TITLE.locale(locale)
                color = Colors.DEFAULT.value
                description =  buildString {
                    append("**${WeeklyMarketLocale.DESCRIPTION.locale(locale)}**\n")
                    entries.forEach { (item, requirement) ->
                        append("${item.name}: ${requirement.cost} MO\n")
                    }
                }
            }
            actionRow {
                stringSelect(buildElementId(compactUuid())) {
                    keys.forEach { item ->
                        option(item.name, idMap.entries.first { it.value == item.name }.key)
                    }
                }
            }
            actionRow {
                interactionButton(ButtonStyle.Primary, buildElementId(compactUuid())) {
                    label = WeeklyMarketLocale.BUY.locale(locale)
                }
            }
        }
    }

    /**
     * Generates a new market with random objects for each guild that activated this event.
     * The function will:
     * 1. Disable the previous market.
     * 2. Generate a new set of [Item]s to put in the market. The items will be saved with a short id in a cache, to
     * avoid storing information in the component ids.
     * 3. Generate and publish a new market message.
     */
    private suspend fun generateNewMarket(guildId: Snowflake) {
        disablePreviousMarket(guildId)
        val newItems = categories.entries.fold(emptyMap<Item, CraftRequirement>()) { acc, (label, qty) ->
            acc + retrieveItemsForCategory(guildId, label, qty)
        }
        val idToItems = newItems.keys.associate { shortId() to it.name }
        val message = kord.getChannelOfType(guildId, Channels.MARKET_CHANNEL, cacheManager).createMessage(
            newItems.generateMessage(guildId, idToItems)
        )
        val market = WeeklyMarket(
            Date(),
            message.id.toString(),
            idToItems = idToItems,
            items = newItems.mapKeys { (k, _) -> k.name }
        )
        marketCache.put(guildId, market)
        db.utilityScope.updateMarket(guildId.toString(), market)
    }

    override fun register() {
        handleButton()
        handleSelection()
        runBlocking {
            kord.guilds.collect { guild ->
                if (cacheManager.getConfig(guild.id).eventChannels[eventId]?.enabled == true) {
                    taskExecutorScope.launch {
                        val schedulerConfig = "0 55 20 * * ${getTimezoneOffset()}o 2,3w"
                        logger.info { "Starting Periodic Market for guild ${guild.name} at $schedulerConfig" }
                        doInfinity(schedulerConfig) {
                            try {
                                logger.info { it }
                                generateNewMarket(guild.id)
                            } catch (e: Exception) {
                                logger.info { "Smoething went wrong" }
                                logger.error { e.stackTraceToString() }
                            }
                        }
                    }
                }
            }
        }
    }

}