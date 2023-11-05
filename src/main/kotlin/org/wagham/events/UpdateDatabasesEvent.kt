package org.wagham.events

import dev.inmo.krontab.doInfinity
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.entity.channel.MessageChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.litote.kmongo.eq
import org.litote.kmongo.`in`
import org.wagham.annotations.BotEvent
import org.wagham.components.CacheManager
import org.wagham.config.Channels
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.Item
import org.wagham.sheets.data.*
import org.wagham.utils.associateBy
import org.wagham.utils.associateTo
import org.wagham.utils.getStartingInstantOnNextDay
import org.wagham.utils.getTimezoneOffset
import org.wagham.utils.sendTextMessage
import java.util.*
import kotlin.concurrent.schedule

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

    private suspend fun updateAnnouncements(guildId: Snowflake) =
        try {
            val announcements = AnnouncementRow.parseRows("prizes")
            val result = db.utilityScope.updateAnnouncements(guildId.toString(), "prizes", announcements.announcements)
            if (result)
                getLogChannel(guildId).sendTextMessage("Successfully refreshed announcements")
            else
                getLogChannel(guildId).sendTextMessage("There was an error refreshing announcements")
        } catch (e: Exception) {
            getLogChannel(guildId).sendTextMessage("There was an error refreshing announcements: ${e.message}")
        }
    private suspend fun updateBackgrounds(guildId: Snowflake) =
        try {
            val backgrounds = BackgroundRow.parseRows()
            val result = db.backgroundsScope
                .rewriteAllBackgrounds(
                    guildId.toString(),
                    backgrounds.filter { it.operation == ImportOperation.UPDATE }.map { it.background }
                )
            if (result)
                getLogChannel(guildId).sendTextMessage("Successfully refreshed backgrounds")
            else
                getLogChannel(guildId).sendTextMessage("There was an error refreshing backgrounds")
        } catch (e: Exception) {
            getLogChannel(guildId).sendTextMessage("There was an error refreshing backgrounds: ${e.message}")
        }
    private suspend fun updateBounties(guildId: Snowflake) =
        try {
            val bounties = BountiesRow.parseRows()
            val result = db.bountiesScope
                .rewriteAllBounties(
                    guildId.toString(),
                    bounties.filter { it.operation == ImportOperation.UPDATE }.map { it.bounty }
                )
            if (result)
                getLogChannel(guildId).sendTextMessage("Successfully refreshed bounties")
            else
                getLogChannel(guildId).sendTextMessage("There was an error refreshing bounties")
        } catch (e: Exception) {
            getLogChannel(guildId).sendTextMessage("There was an error refreshing bounties: ${e.message}")
        }
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
    private suspend fun updateClasses(guildId: Snowflake) =
        try {
            val classes = DndClassRow.parseRows()
            val result = db.subclassesScope
                .rewriteAllSubclasses(
                    guildId.toString(),
                    classes.filter { it.operation == ImportOperation.UPDATE }.map { it.dndClass }
                )
            if (result)
                getLogChannel(guildId).sendTextMessage("Successfully refreshed classes and subclasses")
            else
                getLogChannel(guildId).sendTextMessage("There was an error refreshing classes and subclasses")
        } catch (e: Exception) {
            getLogChannel(guildId).sendTextMessage("There was an error refreshing classes and subclasses: ${e.message}")
        }
    private suspend fun updateFeats(guildId: Snowflake) =
        try {
            val feats = FeatRow.parseRows()
            val result = db.featsScope
                .rewriteAllFeats(
                    guildId.toString(),
                    feats.filter { it.operation == ImportOperation.UPDATE }.map { it.feat }
                )
            if (result)
                getLogChannel(guildId).sendTextMessage("Successfully refreshed feats")
            else
                getLogChannel(guildId).sendTextMessage("There was an error refreshing feats")
        } catch (e: Exception) {
            getLogChannel(guildId).sendTextMessage("There was an error refreshing feats: ${e.message}")
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
                        itemsToDelete.fold(it) { res, item ->
                            res && db.charactersScope.removeItemFromAllInventories(session, guildId.toString(), item)
                        }
                    }
                    if (transactionResult.committed) getLogChannel(guildId).sendTextMessage("${itemsToDelete.size} items deleted")
                    else getLogChannel(guildId).sendTextMessage("There was an error deleting items: ${transactionResult.exception?.stackTraceToString()}")
                }
            }
        } catch (e: Exception) {
            getLogChannel(guildId).sendTextMessage("There was an error refreshing items:\n${e.stackTraceToString()}")
        }
    }
    private suspend fun updateRaces(guildId: Snowflake) =
        try {
            val races = RaceRow.parseRows()
            val result = db.raceScope
                .rewriteAllRaces(
                    guildId.toString(),
                    races.filter { it.operation == ImportOperation.UPDATE }.map { it.race }
                )
            if (result)
                getLogChannel(guildId).sendTextMessage("Successfully refreshed races")
            else
                getLogChannel(guildId).sendTextMessage("There was an error refreshing races")
        } catch (e: Exception) {
            getLogChannel(guildId).sendTextMessage("There was an error refreshing races: ${e.message}")
        }
    private suspend fun updateSpells(guildId: Snowflake) =
        try {
            val spells = SpellRow.parseRows()
            val result = db.spellsScope
                .rewriteAllSpells(
                    guildId.toString(),
                    spells.filter { it.operation == ImportOperation.UPDATE }.map { it.spell }
                )
            if (result)
                getLogChannel(guildId).sendTextMessage("Successfully refreshed spells")
            else
                getLogChannel(guildId).sendTextMessage("There was an error refreshing spells")
        } catch (e: Exception) {
            getLogChannel(guildId).sendTextMessage("There was an error refreshing spells: ${e.message}")
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
                        val schedulerConfig = "0 0 10 * * ${getTimezoneOffset()}o"
                        logger.info { "Starting Update Database for guild ${guild.name} at $schedulerConfig" }
                        doInfinity(schedulerConfig) {
                            // updateAnnouncements(guild.id)
                            // updateBackgrounds(guild.id)
                            // updateBounties(guild.id)
                            // updateBuildings(guild.id)
                            // updateClasses(guild.id)
                            // updateFeats(guild.id)
                            updateItems(guild.id)
                            // updateRaces(guild.id)
                            // updateSpells(guild.id)
                            // updateLanguages(guild.id)
                            // updateTools(guild.id)
                        }
                    }
                }
            }
        }
    }

}