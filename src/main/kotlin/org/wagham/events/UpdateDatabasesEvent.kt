package org.wagham.events

import dev.inmo.krontab.doInfinity
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.entity.channel.MessageChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.wagham.annotations.BotEvent
import org.wagham.components.CacheManager
import org.wagham.config.Channels
import org.wagham.db.KabotMultiDBClient
import org.wagham.sheets.data.*
import org.wagham.utils.associateBy
import org.wagham.utils.getTimezoneOffset
import org.wagham.utils.sendTextMessage

@BotEvent("wagham")
class UpdateDatabasesEvent(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : Event {

    override val eventId = "update_databases"
    private val taskExecutorScope = CoroutineScope(Dispatchers.IO)
    private val logger = KotlinLogging.logger {}

    private suspend fun getLogChannel(guildId: Snowflake) =
        cacheManager.getConfig(guildId).channels[Channels.LOG_CHANNEL.name]
            ?.let { Snowflake(it) }
            ?.let {  kord.defaultSupplier.getChannel(it).asChannelOf<MessageChannel>() }
            ?: kord.defaultSupplier.getGuild(guildId).getSystemChannel()
            ?: throw Exception("Log channel not found")

    private suspend fun updateBuildings(guildId: Snowflake) {
        try {
            val buildings = BuildingRecipeRow.parseRows()
            val buildingsToUpdate =
                buildings.filter { it.operation == ImportOperation.UPDATE }.map { it.buildingRecipe }
            val buildingsToDelete =
                buildings.filter { it.operation == ImportOperation.DELETE }.map { it.buildingRecipe.name }
            if (buildingsToUpdate.isNotEmpty()) {
                db.buildingsScope.updateBuildings(guildId.toString(), buildingsToUpdate).let {
                    if (it) getLogChannel(guildId).sendTextMessage("${buildingsToUpdate.size} buildings updated")
                    else getLogChannel(guildId).sendTextMessage("There was an error updating buildings")
                }
            }
            if (buildingsToDelete.isNotEmpty()) {
                db.buildingsScope.deleteBuildings(guildId.toString(), buildingsToDelete).let {
                    if (it) getLogChannel(guildId).sendTextMessage("${buildingsToDelete.size} buildings deleted")
                    else getLogChannel(guildId).sendTextMessage("There was an error deleting buildings")
                }
            }

        } catch (e: Exception) {
            getLogChannel(guildId).sendTextMessage("There was an error refreshing buildings: ${e.message}")
        }
    }

    private suspend fun updateItems(guildId: Snowflake) {
        try {
            val labelsByName = db.labelsScope.getLabels(guildId.toString()).associateBy {
                it.name
            }
            val items = ItemRow.parseRows(labelsByName)
            val itemsToUpdate = items.filter { it.operation == ImportOperation.UPDATE }.map { it.item }
            val itemsToDelete = items.filter { it.operation == ImportOperation.DELETE }.map { it.item.name }
            if (itemsToUpdate.isNotEmpty()) {
                db.itemsScope.createOrUpdateItems(guildId.toString(), itemsToUpdate).let { result ->
                    if (result.committed) getLogChannel(guildId).sendTextMessage("**Updated ${itemsToUpdate.size} items**")
                    else getLogChannel(guildId).sendTextMessage("**There was an error updating items**:\n${result.exception?.stackTraceToString()}")
                }
            }
            if(itemsToDelete.isNotEmpty()) {
                db.itemsScope.deleteItems(guildId.toString(), itemsToDelete).let {
                    val transactionResult = db.transaction(guildId.toString()) { session ->
                        itemsToDelete.forEach { item ->
                            db.charactersScope.removeItemFromAllInventories(session, guildId.toString(), item)
                        }
                    }
                    if (transactionResult.committed) getLogChannel(guildId).sendTextMessage("${itemsToDelete.size} items deleted")
                    else getLogChannel(guildId).sendTextMessage("There was an error deleting items: ${transactionResult.exception?.stackTraceToString()}")
                }
            }

            val errors = items.filter { it.operation == ImportOperation.ERROR }
            if(errors.isNotEmpty()) {
                val channel = getLogChannel(guildId)
                channel.sendTextMessage("There was an error refreshing the following items:")
                errors.forEach {
                    channel.sendTextMessage("${it.item.name}\n${it.errorMessage}")
                }
            }
        } catch (e: Exception) {
            getLogChannel(guildId).sendTextMessage("There was an error refreshing items:\n${e.stackTraceToString()}")
        }
    }

    private suspend fun updateLanguages(guildId: Snowflake) =
        try {
            val languages = LanguageProficiencyRow.parseRows()
            val result = db.proficiencyScope
                .rewriteAllLanguages(
                    guildId.toString(),
                    languages.filter { it.operation == ImportOperation.UPDATE }.map { it.language }
                )
            if (result)
                getLogChannel(guildId).sendTextMessage("Successfully refreshed languages")
            else
                getLogChannel(guildId).sendTextMessage("There was an error refreshing languages")
        } catch (e: Exception) {
            getLogChannel(guildId).sendTextMessage("There was an error refreshing languages: ${e.message}")
        }

    private suspend fun updateTools(guildId: Snowflake) =
        try {
            val tools = ToolProficiencyRow.parseRows()
            val result = db.proficiencyScope
                .rewriteAllToolProficiencies(
                    guildId.toString(),
                    tools.filter { it.operation == ImportOperation.UPDATE }.map { it.tool }
                )
            if (result)
                getLogChannel(guildId).sendTextMessage("Successfully refreshed tools")
            else
                getLogChannel(guildId).sendTextMessage("There was an error refreshing tools")
        } catch (e: Exception) {
            getLogChannel(guildId).sendTextMessage("There was an error refreshing tools: ${e.message}")
        }

    override fun register() {
        runBlocking {
            kord.guilds.collect { guild ->
                if (cacheManager.getConfig(guild.id).eventChannels[eventId]?.enabled == true) {
                    taskExecutorScope.launch {
                        val schedulerConfig = "0 0 1 * * ${getTimezoneOffset()}o"
                        logger.info { "Starting Update Database for guild ${guild.name} at $schedulerConfig" }
                        doInfinity(schedulerConfig) {
                            updateBuildings(guild.id)
//                            updateItems(guild.id)
                            updateLanguages(guild.id)
                            updateTools(guild.id)
                        }
                    }
                }
            }
        }
    }

}