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
import org.wagham.db.enums.TransactionType
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Announcement
import org.wagham.db.models.AnnouncementType
import org.wagham.db.models.Character
import org.wagham.db.models.embed.Transaction
import org.wagham.utils.*
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
        calendar[Calendar.HOUR_OF_DAY] = 0
        calendar[Calendar.MINUTE] = 0
        calendar[Calendar.SECOND] = 0
        calendar[Calendar.MILLISECOND] = 0
        calendar[Calendar.DAY_OF_WEEK] = Calendar.MONDAY
        calendar.add(Calendar.DAY_OF_WEEK, -7)
        val firstDay = calendar.time
        calendar.add(Calendar.DAY_OF_WEEK, 6)
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
                character.player to (tierRewards[tier]!! * 2 * it.value)
            }.toMap()
    }

    private suspend fun getDelegateRewards(guildId: Snowflake): Map<String, Int> {
        val expTable = cacheManager.getExpTable(guildId)
        return kord
            .getGuildOrNull(guildId)
            ?.members
            ?.filter { m ->
                m.roles.toList().any { it.name == "Delegato" }
            }?.toList()
            ?.mapNotNull { member ->
                try {
                    db.charactersScope
                        .getActiveCharacters(guildId.toString(), member.id.toString())
                        .toList()
                        .takeIf { characters ->
                            characters.isNotEmpty() && characters.any {it.hasActivityInLast30Days() }
                        }?.let { characters ->
                            characters.minByOrNull { it.created ?: Date() } ?: characters.first()
                        }?.let { character ->
                            val tier = expTable.expToTier(character.ms().toFloat())
                            member.id.toString() to (tierRewards[tier]!! * 2)
                        }
                } catch (e: NoActiveCharacterException) {
                    null
                }
            }?.toMap() ?: emptyMap()
    }

    private fun Character.hasActivityInLast30Days() =
        maxOrNull(
            created,
            maxOrNull(lastPlayed, lastMastered)
        )?.let {
            daysToToday(it) <= 30
        } ?: false

    private fun getAllEligibleCharacters(guildId: Snowflake) =
        db.charactersScope.getAllCharacters(guildId.toString(), CharacterStatus.active)
            .filter { character -> character.hasActivityInLast30Days() }

    private fun getBuildingTierAsInt(buildingId: String) =
        buildingId.split(":").last().last().digitToIntOrNull() ?: 6

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
            70,
            getMasterRewards(guildId, weekStart, weekEnd),
            getDelegateRewards(guildId)
        )
        val expTable = cacheManager.getExpTable(guildId)


        // For each active player
        val updatedLog = getAllEligibleCharacters(guildId)
            .filter { it.buildings.isNotEmpty() }
            .fold(rewardsLog) { log, character ->
                val tier = expTable.expToTier(character.ms().toFloat())

                // Calculates the buildings rewards
                val buildingsLog = character.buildings.entries
                    .filter { it.value.isNotEmpty() &&
                            getBuildingTierAsInt(it.key) <= tier.toInt()
                    }.fold(emptyMap<String, BuildingReward>()) { bLog, (buildingId, buildings) ->
                        val buildingName = buildingId.split(":").first()
                        val prize = bounties[buildingName]!!.sample()
                        val additionalItem = prize.randomItems.takeIf { it.isNotEmpty() }?.let { p ->
                            DiscreteProbabilityCollectionSampler(
                                RandomSource.XO_RO_SHI_RO_128_PP.create(),
                                p.associateWith { it.probability.toDouble() }
                            ).sample()
                        }
                        val buildingRewardLog = BuildingReward(
                            announcementType = prize.announceId,
                            announcement = prize.announceId?.let { buildings.random().name to announcements.getAnnouncement(it) },
                            money = prize.moneyDelta.toFloat(),
                            items = prize.guaranteedItems + (additionalItem?.let { mapOf(it.itemId to it.qty) } ?: emptyMap())
                        )
                        bLog + (buildingName to buildingRewardLog)
                    }
                log.copy(
                    playerRewards = log.playerRewards + (character.player to buildingsLog)
                )
            }

        val transactionResult = db.transaction(guildId.toString()) { session ->
            getAllEligibleCharacters(guildId).fold(true) { status, character ->
                    val moneyToGive = (updatedLog.master[character.player]?.toFloat() ?: 0f) +
                            (updatedLog.delegates[character.player]?.toFloat() ?: 0f) +
                            (updatedLog.playerRewards[character.player]
                                ?.values
                                ?.map{ it.money }?.sum() ?: 0f)
                    val moneyResult = if(moneyToGive > 0f) {
                        db.charactersScope.addMoney(session, guildId.toString(), character.id, moneyToGive).also {
                            if(!it) logger.warn { "Money failure: ${character.id}" }
                        }
                    } else true


                    val itemsToGive = updatedLog.playerRewards[character.player]?.values
                        ?.flatMap { it.items.entries }
                        ?.fold(emptyMap<String, Int>()) { acc, it ->
                            acc + (it.key to (acc[it.key]?.plus(it.value) ?: it.value))
                        } ?: emptyMap()
                    val itemsResult = itemsToGive.entries.fold(true) { acc, it ->
                        acc && db.charactersScope.addItemToInventory(
                            session,
                            guildId.toString(),
                            character.id,
                            it.key,
                            it.value
                        ).also { res ->
                            if(!res) logger.warn { "Item failure (${it.key}): ${character.id}" }
                        }
                    }

                    val tier = expTable.expToTier(character.ms().toFloat())
                    val tBadgeResults = db.charactersScope.addItemToInventory(
                        session,
                        guildId.toString(),
                        character.id,
                        "1DayT${tier}Badge",
                        updatedLog.tBadge
                    ).also {
                        if(!it) logger.warn { "Tbadge failure: ${character.id}" }
                    }

                    val itemsForTransaction = itemsToGive.mapValues { it.value.toFloat() } +
                            mapOf("1DayT${tier}Badge" to updatedLog.tBadge.toFloat()) +
                            (mapOf(transactionMoney to moneyToGive).takeIf { moneyToGive > 0f } ?: emptyMap())

                    val recordStep = db.characterTransactionsScope.addTransactionForCharacter(
                        session, guildId.toString(), character.id, Transaction(
                            Date(), null, "REWARDS", TransactionType.ADD, itemsForTransaction
                        )
                    ).also {
                        if(!it) logger.warn { "Record failure: ${character.id}" }
                    }

                    status && moneyResult && itemsResult && tBadgeResults && recordStep
                }
        }

        getChannel(guildId, Channels.LOG_CHANNEL).sendTextMessage(buildString {
            if (transactionResult.committed) append("Successfully assigned everything")
            else append("Error ${transactionResult.exception?.stackTraceToString()}")
        })
        getChannel(guildId, Channels.MESSAGE_CHANNEL).sendTextMessage("Dlin-Dlon! TBadge, premi master e stipendi sono stati assegnati! Godetevi le vostre ricchezze, maledetti! :moneybag:")
        getChannel(guildId, Channels.BOT_CHANNEL).sendTextMessage(updatedLog.rewardsMessage())
        getChannel(guildId, Channels.MESSAGE_CHANNEL).sendTextMessage(updatedLog.jackpotMessage())
    }

    private suspend fun getChannel(guildId: Snowflake, channelType: Channels) =
        cacheManager.getConfig(guildId).channels[channelType.name]
            ?.let { Snowflake(it) }
            ?.let {  kord.defaultSupplier.getChannel(it).asChannelOf<MessageChannel>() }
            ?: kord.defaultSupplier.getGuild(guildId).getSystemChannel()
            ?: throw Exception("$channelType channel not found")

    override fun register() {
        Timer(eventId).schedule(
            getStartingInstantOnNextDay(18, 0, 0){
                it.with(TemporalAdjusters.next(DayOfWeek.TUESDAY))
            }.also {
                logger.info { "$eventId will start on $it"  }
            },
            7 * 24 * 60 * 60 * 1000
        ) {
            runBlocking {
                kord.guilds.collect {
                    if(cacheManager.getConfig(it.id).eventChannels[eventId]?.enabled == true) {
                        giveRewards(it.id)
                    }
                }
            }
        }
    }

    private data class RewardsLog(
        val weekStart: Date,
        val weekEnd: Date,
        val tBadge: Int,
        val master: Map<String, Int>,
        val delegates: Map<String, Int> = emptyMap(),
        val playerRewards: Map<String, Map<String, BuildingReward>> = emptyMap()
    ) {

        fun jackpotMessage() = buildString {
            append("**Cosa è successo nei vari negozi questa settimana?**")
            playerRewards.forEach { (player, rewards) ->
                rewards.values
                    .mapNotNull {
                        it.announcement
                    }
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