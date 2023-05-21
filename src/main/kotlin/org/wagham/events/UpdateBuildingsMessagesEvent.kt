package org.wagham.events

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.channel.MessageChannel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.wagham.annotations.BotEvent
import org.wagham.components.CacheManager
import org.wagham.config.Channels
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.CharacterStatus
import org.wagham.db.models.Building
import org.wagham.db.models.BuildingMessage
import org.wagham.db.models.PlayerBuildingsMessages
import org.wagham.utils.getChannelOfType
import org.wagham.utils.getStartingInstantOnNextDay
import org.wagham.utils.sendTextMessage
import java.lang.IllegalStateException
import java.util.*
import kotlin.concurrent.schedule

@BotEvent("all")
class UpdateBuildingsMessagesEvent(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : Event {

    override val eventId = "update_buildings_messages"
    private val logger = KotlinLogging.logger {}

    private suspend fun getExistingMessages(guildId: Snowflake) =
        db.utilityScope.getBuildingsMessages(guildId.toString())
            .toList()
            .associateBy { it.id }

    private suspend fun getPlayersWithBuildings(guildId: Snowflake) =
        db.charactersScope.getAllCharacters(guildId.toString(), CharacterStatus.active)
            .filter { it.buildings.isNotEmpty() }
            .toList()
            .associate {
                "${it.player}:${it.name}" to it.buildings.values.flatten().filter { b -> b.status == "active" }
            }

    private suspend fun getBuildingsChannel(guildId: Snowflake) =
        kord.getChannelOfType(guildId, Channels.BUILDINGS_CHANNEL, cacheManager)
    private suspend fun getLogChannel(guildId: Snowflake) =
        kord.getChannelOfType(guildId, Channels.LOG_CHANNEL, cacheManager)

    private suspend fun deleteMessages(guildId: Snowflake, messages: List<Snowflake>) =
        try {
            getBuildingsChannel(guildId).let { channel ->
                val last = channel.getLastMessage() ?: throw IllegalStateException("Last message not found")
                channel.getMessagesBefore(last.id)
                    .filter { messages.contains(it.id)  }
                    .collect{
                        it.delete()
                    }
                if (messages.contains(last.id)) last.delete()
            }
        } catch (e: Exception) {
            getLogChannel(guildId)
                .sendTextMessage("An error occurred refreshing buildings messages\n${e.stackTraceToString()}")
        }

    private suspend fun createMessages(
        channel: MessageChannel,
        player: String,
        buildings: List<Building>
    ): Pair<String, List<BuildingMessage>> {
        val headerMsg = channel.createMessage { content = "**Edifici di <@!$player>:**" }.id
        val newBuildings = buildings.map {
            val msg = buildString {
                append("\t${it.name}\n")
                append("\t*${it.description}*\n\n")
            }
            BuildingMessage(
                channel.createMessage { content = msg }.id.toString(),
                it.name,
                it.zone,
                it.description
            )
        }
        return Pair(headerMsg.toString(), newBuildings)
    }

    private suspend fun regenerateMessages(
        guildId: Snowflake,
        buildings: List<Building>,
        message: PlayerBuildingsMessages
    ): PlayerBuildingsMessages {
        val channel = getBuildingsChannel(guildId)
        deleteMessages(
            guildId,
            listOf(Snowflake(message.headerMessage)) + message.messages.map { Snowflake(it.messageId) }
        )
        val (header, newBuildings) = createMessages(channel, message.player, buildings)
        return message.copy(
            headerMessage = header,
            messages = newBuildings
        )
    }

    private suspend fun updateOrRegenerateMessage(
        guildId: Snowflake,
        buildings: List<Building>,
        currentMessages: PlayerBuildingsMessages
    ): PlayerBuildingsMessages {
        val channel = getBuildingsChannel(guildId)
        return if(buildings.size != currentMessages.messages.size
            || buildings.any { !currentMessages.messages.map { m -> m.buildingName }.contains(it.name) })
        {
            regenerateMessages(guildId, buildings, currentMessages)
        } else {
            val newMessages = currentMessages.messages.map { m ->
                val building = buildings.first { it.name == m.buildingName }
                if (building.zone != m.zone || building.description != m.description) {
                    channel.getMessage(Snowflake(m.messageId)).edit {
                        content = buildString {
                            append("\t${building.name} (${building.zone})\n")
                            append("\t*${building.description}*\n\n")
                        }
                    }
                    m.copy(zone = building.zone, description = building.description)
                } else {
                    m
                }
            }
            currentMessages.copy(
                messages = newMessages
            )
        }
    }

    private suspend fun updateBuildingsMessages(guildId: Snowflake) {
        val currentBuildings = getPlayersWithBuildings(guildId)
        val existingMessages = getExistingMessages(guildId).mapNotNull { (playerMessageId, message) ->
            if(currentBuildings.containsKey(playerMessageId)) {
                playerMessageId to message
            } else {
                deleteMessages(
                    guildId,
                    listOf(Snowflake(message.headerMessage)) + message.messages.map { Snowflake(it.messageId) }
                )
                null
            }
        }.toMap()
        val channel = getBuildingsChannel(guildId)
        val newMessages = currentBuildings.map { (id, buildings) ->
            if(existingMessages.containsKey(id)) {
                updateOrRegenerateMessage(
                    guildId, buildings, existingMessages[id]!!
                )
            } else {
                val (header, newBuildings) = createMessages(channel, id.split(":")[0], buildings)
                PlayerBuildingsMessages(
                    id = id,
                    player = id.split(":")[0],
                    headerMessage = header,
                    messages = newBuildings
                )
            }
        }
        newMessages.forEach {
            db.utilityScope.updateBuildingMessage(guildId.toString(), it)
        }
    }

    override fun register() {
        Timer(eventId).schedule(
            getStartingInstantOnNextDay(0, 10, 0).also {
                logger.info { "$eventId will start on $it"  }
            },
            24 * 60 * 60 * 1000
        ) {
            runBlocking {
                kord.guilds.collect {
                    if(cacheManager.getConfig(it.id).eventChannels[eventId]?.enabled == true) {
                        updateBuildingsMessages(it.id)
                    }
                }
            }
        }
    }

}