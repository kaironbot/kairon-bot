package org.wagham.events

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.entity.channel.MessageChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.wagham.components.CacheManager
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.CharacterStatus
import org.apache.commons.rng.sampling.DiscreteProbabilityCollectionSampler
import org.apache.commons.rng.simple.RandomSource
import org.wagham.annotations.BotEvent
import org.wagham.config.Channels
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Announcement
import org.wagham.db.models.AnnouncementType
import org.wagham.utils.getStartingInstantOnNextDay
import org.wagham.utils.sendTextMessage
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.util.*
import kotlin.concurrent.schedule

@BotEvent("wagham")
class WaghamWeeklyRewardsEvent(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : Event {

    override val eventId = "wagham_weekly_rewards"

    private val logger = KotlinLogging.logger {}
    private val tierRewards = mapOf(
        "1" to 15,
        "2" to 30,
        "3" to 100,
        "4" to 500,
        "5" to 1000
    )

    private fun getWeekStartAndEnd() : Pair<Date, Date>{
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.add(Calendar.DAY_OF_WEEK, -7)
        val firstDay = calendar.time
        calendar.add(Calendar.DAY_OF_WEEK, 7)
        val lastDay = calendar.time
        return Pair(firstDay, lastDay)
    }

    private suspend fun getMasterRewards(guildId: Snowflake, startDate: Date, endDate: Date): Map<String,Int> {
        val expTable = cacheManager.getExpTable(guildId)
        return db.sessionScope
            .getAllSessions(guildId.toString(), startDate = startDate, endDate = endDate)
            .fold(emptyMap<String, Int>()) { acc, it ->
                acc + (it.master to (acc[it.master] ?: 0) + 1)
            }.map {
                val character = db.charactersScope.getCharacter(guildId.toString(), it.key)
                val tier = expTable.expToTier(character.ms().toFloat())
                character.player to (tierRewards[tier]!! * it.value)
            }.toMap()
    }

    private suspend fun getDelegateRewards(guildId: Snowflake): Map<String, Int> {
        val expTable = cacheManager.getExpTable(guildId)
        return kord
            .getGuildOrNull(guildId)
            ?.members
            ?.filter { m ->
                m.roles.toList().any { it.name == "Delegato di Gilda" }
            }?.toList()
            ?.mapNotNull {
                try {
                    val character = db.charactersScope.getActiveCharacter(guildId.toString(), it.id.toString())
                    val tier = expTable.expToTier(character.ms().toFloat())
                    it.id.toString() to (tierRewards[tier]!! * 2)
                } catch (e: NoActiveCharacterException) {
                    null
                }
            }?.toMap() ?: emptyMap()
    }

    private suspend fun giveRewards(guildId: Snowflake) {
        val bounties = db.buildingsScope.getBuildingsWithBounty(guildId.toString())
            .toList().associate { bWb ->
                bWb.name to DiscreteProbabilityCollectionSampler(
                    RandomSource.XO_RO_SHI_RO_128_PP.create(),
                    bWb.bounty.prizes.associateWith { it.probability.toDouble() }
                )
            }
        val announcements = db.utilityScope.getAnnouncements(guildId.toString(), "prizes")

        // Gets the prerequisite data
        val (weekStart, weekEnd) = getWeekStartAndEnd()
        val rewardsLog = RewardsLog(
            weekStart,
            weekEnd,
            db.sessionScope.getTimePassedInGame(guildId.toString(), weekStart, weekEnd),
            getMasterRewards(guildId, weekStart, weekEnd),
            getDelegateRewards(guildId)
        )
        val expTable = cacheManager.getExpTable(guildId)

        // For each active player
        val updatedLog = db.charactersScope.getAllCharacters(guildId.toString(), CharacterStatus.active)
            .filter { it.buildings.isNotEmpty() }
            .fold(rewardsLog) { log, character ->
                val tier = expTable.expToTier(character.ms().toFloat())

                // Calculates the buildings rewards
                val buildingsLog = character.buildings.entries
                    .filter { it.value.isNotEmpty() &&
                            (it.key.last().digitToIntOrNull() ?: 6) <= tier.toInt()
                    }.fold(emptyMap<String, BuildingReward>()) { bLog, (buildingType, buildings) ->
                        val prize = bounties[buildingType]!!.sample()
                        val additionalItem = prize.prizeList.takeIf { it.isNotEmpty() }?.let { p ->
                            DiscreteProbabilityCollectionSampler(
                                RandomSource.XO_RO_SHI_RO_128_PP.create(),
                                p.associateWith { it.probability.toDouble() }
                            ).sample()
                        }
                        val buildingRewardLog = BuildingReward(
                            announcementType = prize.announceId,
                            announcement = prize.announceId?.let { buildings.random().name to announcements.getAnnouncement(it) },
                            money = prize.moDelta.toFloat(),
                            items = (prize.guaranteedObjectId?.let { mapOf(it to prize.guaranteedObjectDelta) } ?: emptyMap()) +
                                    (additionalItem?.let { mapOf(it.itemId to it.qty) } ?: emptyMap())

                        )
                        bLog + (buildingType to buildingRewardLog)
                    }
                log.copy(
                    playerRewards = log.playerRewards + (character.player to buildingsLog)
                )
            }

//            val transactionResult = db.transaction(guildId.toString()) { session ->
//                db.charactersScope.getAllCharacters(guildId.toString(), CharacterStatus.active)
//                    .fold(true) { status, character ->
//                        val moneyToGive = (updatedLog.master[character.player]?.toFloat() ?: 0f) +
//                                (updatedLog.delegates[character.player]?.toFloat() ?: 0f) +
//                                (updatedLog.playerRewards[character.player]
//                                    ?.values
//                                    ?.map{ it.money }?.sum() ?: 0f)
//                        val moneyResult =
//                            db.charactersScope.addMoney(session, guildId.toString(), character.name, moneyToGive)
//
//                        val itemsToGive = updatedLog.playerRewards[character.player]?.values
//                            ?.flatMap { it.items.entries }
//                            ?.fold(emptyMap<String, Int>()) { acc, it ->
//                                acc + (it.key to (acc[it.key]?.plus(it.value) ?: it.value))
//                            } ?: emptyMap()
//                        val itemsResult = itemsToGive.entries.fold(true) { acc, it ->
//                            acc && db.charactersScope.addItemToInventory(
//                                session,
//                                guildId.toString(),
//                                character.name,
//                                it.key,
//                                it.value
//                            )
//                        }
//                        status && moneyResult && itemsResult
//                    }
//            }

        getLogChannel(guildId).let { channel ->
            channel.sendTextMessage("Dlin-Dlon! TBadge, premi master e stipendi sono stati assegnati! Godetevi le vostre ricchezze, maledetti! :moneybag:")
            channel.sendTextMessage(updatedLog.rewardsMessage())
            channel.sendTextMessage(updatedLog.jackpotMessage())
        }

    }

    private suspend fun getLogChannel(guildId: Snowflake) =
        cacheManager.getConfig(guildId).channels[Channels.LOG_CHANNEL.name]
            ?.let { Snowflake(it) }
            ?.let {  kord.defaultSupplier.getChannel(it).asChannelOf<MessageChannel>() }
            ?: kord.defaultSupplier.getGuild(guildId).getSystemChannel()
            ?: throw Exception("Log channel not found")

    override fun register() {
        Timer(eventId).schedule(
            getStartingInstantOnNextDay(19, 0,0){
                it.with(TemporalAdjusters.next(DayOfWeek.TUESDAY))
            }.also {
                logger.info { "$eventId will start on $it"  }
            },
            7 * 24 * 60 * 60 * 1000
        ) {
            runBlocking {
                kord.guilds
                    .first { it.id.toString() == "699173030722535474" }
                    .let { giveRewards(it.id) }
            }
        }
    }

    private data class RewardsLog(
        val weekStart: Date,
        val weekEnd: Date,
        val tBadge: Long,
        val master: Map<String, Int>,
        val delegates: Map<String, Int> = emptyMap(),
        val playerRewards: Map<String, Map<String, BuildingReward>> = emptyMap()
    ) {

        fun jackpotMessage() = buildString {
            append("**Cosa è successo nei vari negozi questa settimana?**")
            playerRewards.forEach { (player, rewards) ->
                rewards.values
                    .mapNotNull { it.announcement }
                    .forEach { (building, announcement) ->
                        append(announcement.format(mapOf(
                            "PLAYER_ID" to player,
                            "BUILDING_NAME" to building
                        )))
                    }
            }
        }

        fun rewardsMessage() = buildString {
            val dateFormatter = SimpleDateFormat("dd/MM")
            append("**Premi della settimana dal ${dateFormatter.format(weekStart)} al ${dateFormatter.format(weekEnd)}**\n\n")
            append("*TBadge assegnati*: ${tBadge}\n\n")
            append("**Premi master**\n")
            master.entries.forEach {
                append("<@!${it.key}>: ${it.value}\n")
            }
            append("\n**Stipendi Delegati**\n")
            delegates.entries.forEach {
                append("<@!${it.key}>: ${it.value}\n")
            }
            append("\n**Premi Edifici**\n")
            playerRewards.entries.forEach{ (player, buildingsReport) ->
                append("<@!$player>\n")
                val totalMo = buildingsReport.values.map { it.money }.sum()
                append("\t*MO Totali: $totalMo*\n")
                buildingsReport.entries.forEach { (buildingType, prize) ->
                    append("\t*$buildingType ")
                    prize.announcementType?.let { append("($it): ") } ?: append(": ")
                    append("${prize.money} MO ")
                    prize.items.entries.forEach {
                        append(", ${it.key} x${it.value}")
                    }
                    append("*\n")
                }
            }
            append("\n")
        }

    }

    private data class BuildingReward(
        val announcementType: AnnouncementType? = null,
        val announcement: Pair<String, Announcement>? = null,
        val items: Map<String, Int> = emptyMap(),
        val money: Float = 0f
    )

}