package org.wagham.events

import dev.inmo.krontab.doInfinity
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.wagham.annotations.BotEvent
import org.wagham.components.CacheManager
import org.wagham.config.Channels
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.CharacterStatus
import org.wagham.db.enums.LabelType
import org.wagham.db.enums.TransactionType
import org.wagham.db.models.Character
import org.wagham.db.models.Item
import org.wagham.db.models.embed.LabelStub
import org.wagham.db.models.embed.Transaction
import org.wagham.entities.channels.RegisteredSession
import org.wagham.utils.LORE_LABEL_ID
import org.wagham.utils.RECIPE_LABEL_ID
import org.wagham.utils.associateTo
import org.wagham.utils.getChannelOfType
import org.wagham.utils.sendTextMessage
import java.util.*

@BotEvent("wagham")
class AssignItemAfterSessionEvent(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : Event {

    override val eventId = "assign_item_after_session"
    private val taskExecutorScope = CoroutineScope(Dispatchers.Default)
    private var channelDispatcher: Job? = null
    private val logger = KotlinLogging.logger {}

    private suspend fun getRandomItem(guildId: String, labels: List<LabelStub>): Pair<Item, Int> =
        db.itemsScope.getItems(guildId, labels).toList().random().let {
            it to 1
        }

    private fun Map<Character, Pair<Item, Int>>.buildMessage(sessionTitle: String) = buildString {
        append("Gli intrepidi avventurieri che hanno risposto alla missiva **$sessionTitle** sono stati ricompensati:\n\n")
        this@buildMessage.entries.forEach { (participant, item) ->
            append("${participant.name} (<@${participant.player}>): ")
            append("${item.second} ${item.first.name}\n")
        }
    }

    private fun assignItemsToParticipants(registeredSession: RegisteredSession) = taskExecutorScope.launch {
        val session = db.sessionScope.getSessionByUid(registeredSession.guildId, registeredSession.sessionId)
        if(session != null && session.labels.any { it.id == LORE_LABEL_ID }) {
            val itemLabels = db.labelsScope.getLabels(registeredSession.guildId, session.labels.map { it.id } + RECIPE_LABEL_ID, LabelType.ITEM).map {
                it.toLabelStub()
            }.toList()
            if (itemLabels.isNotEmpty()) {
                val characterToItem = db.charactersScope.getCharacters(
                    registeredSession.guildId,
                    session.characters.map { it.character }
                ).filter {
                    it.status == CharacterStatus.active
                }.associateTo {
                    getRandomItem(registeredSession.guildId, itemLabels)
                }
                val transactionResult = db.transaction(registeredSession.guildId) { s ->
                    characterToItem.entries.all { (participant, prize) ->
                        db.charactersScope.addItemToInventory(
                            s,
                            registeredSession.guildId,
                            participant.id,
                            prize.first.name,
                            prize.second
                        ) && db.characterTransactionsScope.addTransactionForCharacter(
                            s, registeredSession.guildId, participant.id, Transaction(
                                Date(), null, "SESSION_REWARD", TransactionType.ADD, mapOf(prize.first.name to prize.second.toFloat())
                            )
                        )
                    }
                }

                if(transactionResult.committed) {
                    val channel = kord.getChannelOfType(Snowflake(registeredSession.guildId), Channels.MESSAGE_CHANNEL, cacheManager)
                    channel.sendTextMessage(characterToItem.buildMessage(session.title))
                } else {
                    val channel = kord.getChannelOfType(Snowflake(registeredSession.guildId), Channels.LOG_CHANNEL, cacheManager)
                    channel.sendTextMessage("Cannot assign recipes for session ${registeredSession.guildId}\n${transactionResult.exception?.stackTraceToString()}")
                }
            }
        }
    }

    private fun launchChannelDispatcher() = taskExecutorScope.launch {
        try {
            val channel = cacheManager.getChannel<AssignItemAfterSessionEvent, RegisteredSession>()
            for(message in channel) {
                assignItemsToParticipants(message)
            }
        } catch (e: Exception) {
            logger.info { "Error while dispatching drop op: ${e.stackTraceToString()}" }
        }
    }

    override fun register() {
        launchChannelDispatcher()
        taskExecutorScope.launch {
            doInfinity("0 * * * *") {
                if (channelDispatcher?.isActive != true) {
                    logger.info { "Dispatcher is dead, relaunching" }
                    channelDispatcher = launchChannelDispatcher()
                }
            }
        }
    }

}