package org.wagham.components

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.Guild
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import mu.KotlinLogging
import org.wagham.config.Channels
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.ScheduledEventArg
import org.wagham.db.enums.ScheduledEventState
import org.wagham.db.enums.ScheduledEventType
import org.wagham.db.enums.TransactionType
import org.wagham.db.models.Item
import org.wagham.db.models.LanguageProficiency
import org.wagham.db.models.ScheduledEvent
import org.wagham.db.models.ToolProficiency
import org.wagham.db.models.embed.ProficiencyStub
import org.wagham.db.models.embed.Transaction
import org.wagham.utils.getChannelOfTypeOrDefault
import org.wagham.utils.sendTextMessage
import java.util.Date

class SchedulingManager(
    val db: KabotMultiDBClient
) {

    private val logger = KotlinLogging.logger {}
    private val taskChannel = Channel<Pair<Snowflake, ScheduledEvent>>(UNLIMITED)

    suspend fun retrieveInterruptedTasks(guilds: Flow<Guild>) = guilds.collect { guild ->
        db.scheduledEventsScope.getAllScheduledEvents(guild.id.toString(), ScheduledEventState.SCHEDULED).collect {
            addToQueue(guild.id, it)
        }
    }

    suspend fun addToQueue(guildId: Snowflake, event: ScheduledEvent) = taskChannel.send(Pair(guildId, event))

    private suspend fun handleGiveItemTask(guildId: Snowflake, task: ScheduledEvent, kord: Kord, cacheManager: CacheManager) {
        val itemToCraft = task.args.getValue(ScheduledEventArg.ITEM)
        val target = task.args.getValue(ScheduledEventArg.TARGET)
        val player = target.split(":").first()
        val quantity = task.args[ScheduledEventArg.INT_QUANTITY]!!.toInt()
        val channel = kord.getChannelOfTypeOrDefault(guildId, Channels.BOT_CHANNEL, cacheManager)
        val item = cacheManager.getCollectionOfType<Item>(guildId).firstOrNull {
            it.name == itemToCraft
        }
        if (item == null) {
            channel.sendTextMessage(
                "<@$player> $itemToCraft cannot be crafted as it does not exist"
            )
            db.scheduledEventsScope.updateState(
                guildId.toString(),
                task.id,
                ScheduledEventState.FAILED
            )
        } else {
            db.transaction(guildId.toString()) { session ->
                val assignStep = db.charactersScope.addItemToInventory(
                    session,
                    guildId.toString(),
                    target,
                    item.name,
                    quantity
                )
                val recordStep = db.characterTransactionsScope.addTransactionForCharacter(
                    session,
                    guildId.toString(),
                    target,
                    Transaction(
                        Date(),
                        null,
                        "CRAFT",
                        TransactionType.ADD,
                        mapOf(item.name to quantity.toFloat())
                    )
                )
                assignStep && recordStep
            }.let { result ->
                if (result.committed) {
                    channel.sendTextMessage("<@$player> successfully assigned ${item.name} x$quantity")
                    db.scheduledEventsScope.updateState(
                        guildId.toString(),
                        task.id,
                        ScheduledEventState.COMPLETED
                    )
                } else {
                    channel.sendTextMessage("<@$player> there was an error assigning ${item.name}")
                    db.scheduledEventsScope.updateState(
                        guildId.toString(),
                        task.id,
                        ScheduledEventState.FAILED
                    )
                }
            }
        }
    }

    private suspend fun handleGiveToolTask(guildId: Snowflake, task: ScheduledEvent, kord: Kord, cacheManager: CacheManager) {
        val toolId = task.args.getValue(ScheduledEventArg.PROFICIENCY_ID)
        val target = task.args.getValue(ScheduledEventArg.TARGET)
        val player = target.split(":").first()
        val channel = kord.getChannelOfTypeOrDefault(guildId, Channels.BOT_CHANNEL, cacheManager)
        val tool = cacheManager.getCollectionOfType<ToolProficiency>(guildId).firstOrNull {
            it.id == toolId
        }
        if (tool == null) {
            channel.sendTextMessage(
                "<@$player> tool $toolId cannot be given as it does not exist"
            )
            db.scheduledEventsScope.updateState(
                guildId.toString(),
                task.id,
                ScheduledEventState.FAILED
            )
        } else {
            db.transaction(guildId.toString()) { session ->
                val assignStep =  db.charactersScope.addProficiencyToCharacter(
                    session,
                    guildId.toString(),
                    target,
                    ProficiencyStub(tool.id, tool.name)
                )
                val recordStep = db.characterTransactionsScope.addTransactionForCharacter(
                    session,
                    guildId.toString(),
                    target,
                    Transaction(Date(), null, "BUY TOOL", TransactionType.ADD, mapOf(tool.name to 1f))
                )
                assignStep && recordStep
            }.let { result ->
                if (result.committed) {
                    channel.sendTextMessage("<@$player> successfully assigned ${tool.name}")
                    db.scheduledEventsScope.updateState(
                        guildId.toString(),
                        task.id,
                        ScheduledEventState.COMPLETED
                    )
                } else {
                    channel.sendTextMessage("<@$player> there was an error assigning ${tool.name}")
                    db.scheduledEventsScope.updateState(
                        guildId.toString(),
                        task.id,
                        ScheduledEventState.FAILED
                    )
                }
            }
        }
    }

    private suspend fun handleGiveLanguageTask(guildId: Snowflake, task: ScheduledEvent, kord: Kord, cacheManager: CacheManager) {
        val languageId = task.args.getValue(ScheduledEventArg.PROFICIENCY_ID)
        val target = task.args.getValue(ScheduledEventArg.TARGET)
        val player = target.split(":").first()
        val channel = kord.getChannelOfTypeOrDefault(guildId, Channels.BOT_CHANNEL, cacheManager)
        val tool = cacheManager.getCollectionOfType<LanguageProficiency>(guildId).firstOrNull {
            it.id == languageId
        }
        if (tool == null) {
            channel.sendTextMessage(
                "<@$player> language $languageId cannot be crafted as it does not exist"
            )
            db.scheduledEventsScope.updateState(
                guildId.toString(),
                task.id,
                ScheduledEventState.FAILED
            )
        } else {
            db.transaction(guildId.toString()) { session ->
                val assignStep =  db.charactersScope.addLanguageToCharacter(
                    session,
                    guildId.toString(),
                    target,
                    ProficiencyStub(tool.id, tool.name)
                )
                val recordStep = db.characterTransactionsScope.addTransactionForCharacter(
                    session,
                    guildId.toString(),
                    target,
                    Transaction(Date(), null, "BUY LANGUAGE", TransactionType.ADD, mapOf(tool.name to 1f))
                )
                assignStep && recordStep
            }.let { result ->
                if (result.committed) {
                    channel.sendTextMessage("<@$player> successfully assigned ${tool.name}")
                    db.scheduledEventsScope.updateState(
                        guildId.toString(),
                        task.id,
                        ScheduledEventState.COMPLETED
                    )
                } else {
                    channel.sendTextMessage("<@$player> there was an error assigning ${tool.name}")
                    db.scheduledEventsScope.updateState(
                        guildId.toString(),
                        task.id,
                        ScheduledEventState.FAILED
                    )
                }
            }
        }
    }

    suspend fun launchTasks(cacheManager: CacheManager, kord: Kord) = coroutineScope {
        for ((guildId, task) in taskChannel) {
            launch(Dispatchers.Default) {
                try {
                    val waitTime = (task.activation.time - System.currentTimeMillis())
                    delay(waitTime)
                    when(task.type) {
                        ScheduledEventType.GIVE_ITEM -> handleGiveItemTask(guildId, task, kord, cacheManager)
                        ScheduledEventType.GIVE_TOOL -> handleGiveToolTask(guildId, task, kord, cacheManager)
                        ScheduledEventType.GIVE_LANGUAGE -> handleGiveLanguageTask(guildId, task, kord, cacheManager)
                    }
                } catch (e: Exception) {
                    db.scheduledEventsScope.updateState(
                        guildId.toString(),
                        task.id,
                        ScheduledEventState.FAILED
                    )
                    logger.warn { "Error in task ${task.id}\n${e.stackTraceToString()}" }
                }
            }
        }
    }

}