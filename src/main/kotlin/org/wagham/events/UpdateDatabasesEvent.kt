package org.wagham.events

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.entity.channel.MessageChannel
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.wagham.annotations.BotEvent
import org.wagham.components.CacheManager
import org.wagham.config.Channels
import org.wagham.db.KabotMultiDBClient
import org.wagham.sheets.data.*
import org.wagham.utils.getStartingInstantOnNextDay
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
            val items = ItemRow.parseRows()
            val itemsToUpdate = items.filter { it.operation == ImportOperation.UPDATE }.map { it.item }
            val itemsToDelete = items.filter { it.operation == ImportOperation.DELETE }.map { it.item.name }
            if (itemsToUpdate.isNotEmpty()) {
                db.itemsScope.updateItems(guildId.toString(), itemsToUpdate).let {
                    if (it) getLogChannel(guildId).sendTextMessage("${itemsToUpdate.size} items updated")
                    else getLogChannel(guildId).sendTextMessage("There was an error updating items")
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
            getLogChannel(guildId).sendTextMessage("There was an error refreshing items: ${e.message}")
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
        Timer(eventId).schedule(
            getStartingInstantOnNextDay(1, 0, 0).also {
                logger.info { "$eventId will start on $it"  }
            },
            24 * 60 * 60 * 1000
        ) {
            runBlocking {
                kord.guilds.collect {
                    if(cacheManager.getConfig(it.id).eventChannels[eventId]?.enabled == true) {
                        updateAnnouncements(it.id)
                        updateBackgrounds(it.id)
                        updateBounties(it.id)
                        updateBuildings(it.id)
                        updateClasses(it.id)
                        updateFeats(it.id)
                        updateItems(it.id)
                        updateRaces(it.id)
                        updateSpells(it.id)
                        updateLanguages(it.id)
                        updateTools(it.id)
                    }
                }
            }
        }
    }

}