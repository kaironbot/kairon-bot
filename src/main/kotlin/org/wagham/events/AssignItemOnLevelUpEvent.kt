package org.wagham.events

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.wagham.annotations.BotEvent
import org.wagham.components.CacheManager
import org.wagham.config.Channels
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.CharacterStatus
import org.wagham.db.models.Character
import org.wagham.db.models.Item
import org.wagham.entities.channels.ExpUpdate
import org.wagham.utils.associateTo
import org.wagham.utils.getChannelOfType
import org.wagham.utils.sendTextMessage
import java.util.*
import kotlin.concurrent.schedule

@BotEvent("wagham")
class AssignItemOnLevelUpEvent(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : Event {

    override val eventId = "assign_item_on_level_up"
    private val taskExecutorScope = CoroutineScope(Dispatchers.Default)
    private var channelDispatcher: Job? = null
    private val logger = KotlinLogging.logger {}

    private suspend fun getRandomItem(guildId: String): Pair<Item, Int> =
        db.itemsScope.getAllItems(guildId).toList().random().let {
            it to 1
        }

    private fun Map<Character, Pair<Item, Int>>.buildMessage() = buildString {
        append("Alcuni avventurieri sono saliti di livello e hanno ricevuto un premio:\n\n")
        this@buildMessage.entries.forEach { (participant, item) ->
            append("${participant.name} (<@${participant.player}>): ")
            append("${item.second} ${item.first.name}\n")
        }
    }

    /**
     * Checks all the [Character]s in the update. If any character leveled up, then it assigns a random item to it.
     *
     * @param update an [ExpUpdate].
     */
    private fun assignItemsOnLevelUp(update: ExpUpdate) = taskExecutorScope.launch {
        val expTable = cacheManager.getExpTable(Snowflake(update.guildId))
        val charactersToItems = db.charactersScope.getCharacters(
            update.guildId,
            update.updates.keys.toList()
        ).filter { character ->
        val expDelta = update.updates.getValue(character.id)
            val currentLevel = expTable.expToLevel(character.ms().toFloat())
            val levelBeforeDelta = expTable.expToLevel(character.ms().toFloat() - expDelta)
            character.status == CharacterStatus.active && currentLevel != levelBeforeDelta
        }.associateTo { getRandomItem(update.guildId) }
        if(charactersToItems.isNotEmpty()) {
            val transactionResult = db.transaction(update.guildId) {
                charactersToItems.entries.all { (character, item) ->
                    db.charactersScope.addItemToInventory(
                        it,
                        update.guildId,
                        character.id,
                        item.first.name,
                        item.second
                    )
                }
            }
            if (transactionResult.committed) {
                kord.getChannelOfType(Snowflake(update.guildId), Channels.MESSAGE_CHANNEL, cacheManager).sendTextMessage(
                    charactersToItems.buildMessage()
                )
            } else {
                val channel = kord.getChannelOfType(Snowflake(update.guildId), Channels.LOG_CHANNEL, cacheManager)
                channel.sendTextMessage("Cannot assign recipes for level up\n${transactionResult.exception?.stackTraceToString()}")
            }
        }
    }

    /**
     * Waits for message to be published to the channel for the class [AssignItemOnLevelUpEvent] and with message type
     * [ExpUpdate], then handles the message.
     */
    private fun launchChannelDispatcher() = taskExecutorScope.launch {
        try {
            val channel = cacheManager.getChannel<AssignItemOnLevelUpEvent, ExpUpdate>()
            for(message in channel) {
                assignItemsOnLevelUp(message)
            }
        } catch (e: Exception) {
            logger.info { "Error while dispatching drop op: ${e.stackTraceToString()}" }
        }
    }

    /**
     * Registers the [Event].
     * It launches a coroutine that registers to the appropriate channels and periodically checks if it is active.
     */
    override fun register() {
        launchChannelDispatcher()
        Timer(eventId).schedule(
            Date(),
            60 * 1000
        ) {
            if (channelDispatcher?.isActive != true) {
                logger.info { "Dispatcher is dead, relaunching" }
                channelDispatcher = launchChannelDispatcher()
            }
        }
    }
}