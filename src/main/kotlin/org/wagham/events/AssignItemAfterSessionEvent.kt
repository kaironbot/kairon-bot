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
import org.wagham.db.models.Session
import org.wagham.db.models.embed.LabelStub
import org.wagham.db.models.embed.Transaction
import org.wagham.entities.channels.RegisteredSession
import org.wagham.utils.*
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
    private val t4Label = LabelStub("290964a2-c86b-4a16-b8c4-cf07cf95dedc", "T4")
    private val t5Label = LabelStub("a8159199-9b4b-4779-8e21-bd6d76ebb0dd", "T5")

    companion object {
        private val tierRewards = mapOf("1" to 20f, "2" to 40f, "3" to 100f, "4" to 200f, "5" to 400f)
    }

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

    private suspend fun assignRecipes(guildId: String, session: Session) {
        val itemLabels = db.labelsScope.getLabels(
            guildId,
            session.labels.map { it.id } + RECIPE_LABEL_ID,
            LabelType.ITEM).map {
            it.toLabelStub()
        }.toList().let { labels ->
            if (labels.contains(t5Label)) labels.filter { it != t5Label } + t4Label
            else labels
        }
        if (itemLabels.isNotEmpty()) {
            val characterToItem = db.charactersScope.getCharacters(
                guildId,
                session.characters.map { it.character }
            ).filter {
                it.status == CharacterStatus.active
            }.associateTo {
                getRandomItem(guildId, itemLabels)
            }
            val transactionResult = db.transaction(guildId) { kabotSession ->
                characterToItem.entries.forEach { (participant, prize) ->
                    db.charactersScope.addItemToInventory(kabotSession, guildId, participant.id, prize.first.name, prize.second)
                    db.characterTransactionsScope.addTransactionForCharacter(
                        kabotSession,
                        guildId,
                        participant.id,
                        Transaction(
                            Date(),
                            null,
                            "SESSION_REWARD",
                            TransactionType.ADD,
                            mapOf(prize.first.name to prize.second.toFloat())
                        )
                    )
                }
            }

            if (transactionResult.committed) {
                val channel = kord.getChannelOfType(Snowflake(guildId), Channels.MESSAGE_CHANNEL, cacheManager)
                channel.sendTextMessage(characterToItem.buildMessage(session.title))
            } else {
                val channel = kord.getChannelOfType(Snowflake(guildId), Channels.LOG_CHANNEL, cacheManager)
                channel.sendTextMessage("Cannot assign recipes for session $guildId\n${transactionResult.exception?.stackTraceToString()}")
            }
        }
    }

    private suspend fun assignMasterReward(guildId: String, session: Session) {
        val character = db.charactersScope.getCharacter(guildId, session.master)
        val expTable = cacheManager.getExpTable(Snowflake(guildId))
        val tier = expTable.expToTier(character.ms().toFloat())
        val reward = tierRewards.getValue(tier)
        val transactionResult = db.transaction(guildId) { kabotSession ->
            db.charactersScope.addMoney(kabotSession, guildId, character.id, reward)
            db.characterTransactionsScope.addTransactionForCharacter(
                kabotSession,
                guildId,
                character.id,
                Transaction(
                    Date(), null, "MASTER REWARD ${session.title}", TransactionType.ADD, mapOf(transactionMoney to reward)
                )
            )
        }

        if (transactionResult.committed) {
            val channel = kord.getChannelOfType(Snowflake(guildId), Channels.MESSAGE_CHANNEL, cacheManager)
            channel.sendTextMessage(buildString {
                append("Per la sessione **")
                append(session.title)
                append("** ${character.name} (<@${character.player}>) riceve ")
                append(reward.toInt())
                append(" MO")
            })
        } else {
            val channel = kord.getChannelOfType(Snowflake(guildId), Channels.LOG_CHANNEL, cacheManager)
            channel.sendTextMessage("Cannot assign recipes for session $guildId\n${transactionResult.exception?.stackTraceToString()}")
        }
    }

    private fun assignItemsToParticipants(registeredSession: RegisteredSession) = taskExecutorScope.launch {
        val session = db.sessionScope.getSessionById(registeredSession.guildId, registeredSession.sessionId)
        if(session != null && session.labels.none { it.id == PBV_LABEL_ID }) {
            if (session.labels.any { it.id == LORE_LABEL_ID }) {
                assignRecipes(registeredSession.guildId, session)
            }
            assignMasterReward(registeredSession.guildId, session)
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