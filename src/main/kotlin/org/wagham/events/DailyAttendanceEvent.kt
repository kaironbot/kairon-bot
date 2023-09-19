package org.wagham.events

import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Guild
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.message.create.UserMessageCreateBuilder
import dev.kord.rest.builder.message.create.actionRow
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.wagham.annotations.BotEvent
import org.wagham.components.CacheManager
import org.wagham.components.Identifiable
import org.wagham.config.Channels
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.events.DailyAttendanceLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.AttendanceReport
import org.wagham.db.models.embed.AttendanceReportPlayer
import org.wagham.entities.channels.UpdateGuildAttendanceMessage
import org.wagham.entities.channels.UpdateGuildAttendanceOperation
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.*
import java.util.*
import kotlin.concurrent.schedule

@BotEvent("all")
class DailyAttendanceEvent(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : Event, Identifiable {

    override val eventId = "daily_attendance"
    private val logger = KotlinLogging.logger {}
    private val taskExecutorScope = CoroutineScope(Dispatchers.Default)
    private val channels = Caffeine.newBuilder().build<Snowflake, Channel<UpdateGuildAttendanceMessage>>()
    private val channelConsumers = Caffeine.newBuilder().build<Snowflake, Job>()

    private fun Map<String, List<AttendanceReportPlayer>>.appendList(buildCtx: StringBuilder) = entries
        .flatMap { (k, v) ->
            v.map {
                it.tier to Pair(k, it)
            }
        }.groupBy { it.first }
        .mapValues { (_, v) ->
            v.map { it.second }
        }.entries
        .sortedBy { it.key }
        .forEach { (tier, reports) ->
            buildCtx.append("**$tier**: ")
            reports.forEach { (p, r) ->
                buildCtx.append("${r.characterName} <@$p> (${r.daysSinceLastPlayed.takeIf { it >= 0 } ?: "-"}), ")
            }
            buildCtx.append("\n")
        }

    private suspend fun buildDescription(locale: String, guildId: Snowflake, newAttendance: Boolean = false) = buildString {
        append("${DailyAttendanceLocale.DESCRIPTION.locale(locale)}\n")
        append("*${DailyAttendanceLocale.LAST_ACTIVITY.locale(locale)}*\n\n")
        db.utilityScope.getLastAttendance(guildId.toString())?.let { report ->
            append("**${DailyAttendanceLocale.AFTERNOON_AVAILABILITY.locale(locale)}**\n")
            if (!newAttendance) {
                report.afternoonPlayers.appendList(this)
            }
            append("\n")
            append("**${DailyAttendanceLocale.EVENING_AVAILABILITY.locale(locale)}**\n")
            if(!newAttendance) {
                report.players.appendList(this)
            }
        }
    }

    private suspend fun getAttendanceReportForPlayer(guildId: String, playerId: String): List<AttendanceReportPlayer> {
        val expTable = cacheManager.getExpTable(Snowflake(guildId))
        return db.charactersScope.getActiveCharacters(guildId, playerId).map {
            AttendanceReportPlayer(
                it.lastPlayed?.let(::daysToToday) ?: -1,
                expTable.expToTier(it.ms().toFloat()),
                it.name
            )
        }.toList()
    }

    private suspend fun prepareMessage(locale: String, guildId: Snowflake, newAttendance: Boolean = false): UserMessageCreateBuilder.() -> Unit {
        val desc = buildDescription(locale, guildId, newAttendance)
        return fun UserMessageCreateBuilder.() {
            embed {
                title = DailyAttendanceLocale.TITLE.locale(locale)
                description = desc
                color = Colors.DEFAULT.value
            }
            actionRow {
                interactionButton(ButtonStyle.Primary, buildElementId("register", "afternoon")) {
                    label = DailyAttendanceLocale.REGISTER_AFTERNOON.locale(locale)
                }
                interactionButton(ButtonStyle.Primary, buildElementId("register", "evening")) {
                    label = DailyAttendanceLocale.REGISTER_EVENING.locale(locale)
                }
                interactionButton(ButtonStyle.Danger, buildElementId("abort", "afternoon")) {
                    label = DailyAttendanceLocale.DEREGISTER_AFTERNOON.locale(locale)
                }
                interactionButton(ButtonStyle.Danger, buildElementId("abort", "evening")) {
                    label = DailyAttendanceLocale.DEREGISTER_EVENING.locale(locale)
                }
            }
        }
    }

    private fun launchChannelDispatcher() = taskExecutorScope.launch {
        val channel = cacheManager.getChannel(DailyAttendanceEvent::class.qualifiedName
            ?: throw IllegalStateException("Cannot find channel id for daily attendance"))
        for(rawMessage in channel) {
            try {
                val message = Json.decodeFromString<UpdateGuildAttendanceMessage>(rawMessage)
                channels.getIfPresent(message.guildId)?.send(message)
            } catch (e: Exception) {
                logger.info { "Error while decoding attendance op: ${e.stackTraceToString()}" }
            }
        }
    }

    private suspend fun removePlayerFromAfternoonAttendance(attendance: AttendanceReport, guildId: String, playerId: String) =
        db.utilityScope.updateAttendance(
            guildId,
            attendance.copy(afternoonPlayers = attendance.afternoonPlayers - playerId)
        )

    private suspend fun removePlayerFromEveningAttendance(attendance: AttendanceReport, guildId: String, playerId: String) =
        db.utilityScope.updateAttendance(guildId, attendance.copy(players = attendance.players - playerId))

    private suspend fun addPlayerToAfternoonAttendance(attendance: AttendanceReport, guildId: String, playerId: String) {
        if(!attendance.afternoonPlayers.containsKey(playerId)) {
            val charactersAttendance = getAttendanceReportForPlayer(guildId, playerId)
            db.utilityScope.updateAttendance(
                guildId,
                attendance.copy(afternoonPlayers = attendance.players + (playerId to charactersAttendance))
            )
        }
    }

    private suspend fun addPlayerToEveningAttendance(attendance: AttendanceReport, guildId: String, playerId: String) {
        if(!attendance.players.containsKey(playerId)) {
            val charactersAttendance = getAttendanceReportForPlayer(guildId, playerId)
            db.utilityScope.updateAttendance(
                guildId,
                attendance.copy(players = attendance.players + (playerId to charactersAttendance))
            )
        }
    }

    private suspend fun refreshAttendance(attendance: AttendanceReport, guildId: String) {
        val attendanceCache = (attendance.players.keys + attendance.afternoonPlayers.keys).associateWith {
            getAttendanceReportForPlayer(guildId, it)
        }
        if(attendanceCache.isNotEmpty()) {
            db.utilityScope.updateAttendance(
                guildId,
                attendance.copy(
                    players = attendance.players.mapValues { (k, v) -> attendanceCache[k] ?: v },
                    afternoonPlayers = attendance.afternoonPlayers.mapValues { (k, v) -> attendanceCache[k] ?: v }
                )
            )
        }
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    private fun launchGuildDispatcher(guildId: Snowflake) = taskExecutorScope.launch {
        val channel = channels.get(guildId) { Channel(UNLIMITED) }
            ?: throw IllegalStateException("Cannot create guild $guildId channel")
        for(message in channel) {
            db.utilityScope.getLastAttendance(message.guildId.toString())?.let { currentAttendance ->
                when {
                    message.operation == null || message.playerId == null ->
                        refreshAttendance(currentAttendance, message.guildId.toString())
                    message.operation == UpdateGuildAttendanceOperation.ABORT_AFTERNOON ->
                        removePlayerFromAfternoonAttendance(currentAttendance, message.guildId.toString(), message.playerId)
                    message.operation == UpdateGuildAttendanceOperation.ABORT_EVENING ->
                        removePlayerFromEveningAttendance(currentAttendance, message.guildId.toString(), message.playerId)
                    message.operation == UpdateGuildAttendanceOperation.REGISTER_AFTERNOON ->
                        addPlayerToAfternoonAttendance(currentAttendance, message.guildId.toString(), message.playerId)
                    message.operation == UpdateGuildAttendanceOperation.REGISTER_EVENING ->
                        addPlayerToEveningAttendance(currentAttendance, message.guildId.toString(), message.playerId)
                }
                if (channel.isEmpty) {
                    val discordChannel = kord.getChannelOfType(guildId, Channels.ATTENDANCE_CHANNEL, cacheManager)
                    val locale = kord.getGuildOrNull(guildId)?.preferredLocale?.language ?: "en"
                    val desc = buildDescription(locale, guildId)
                    discordChannel.getMessage(Snowflake(currentAttendance.message)).edit {
                        embed {
                            title = DailyAttendanceLocale.TITLE.locale(locale)
                            description = desc
                            color = Colors.DEFAULT.value
                        }
                    }
                }
            }
        }
    }

    private fun handleRegistration() {
        kord.on<ButtonInteractionCreateEvent> {
            val guildId = interaction.data.guildId.value ?: throw GuildNotFoundException()
            if(interaction.componentId.startsWith("${this@DailyAttendanceEvent::class.qualifiedName}")) {
                val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
                try {
                    when {
                        interaction.componentId.contains(UpdateGuildAttendanceOperation.ABORT_AFTERNOON.id) -> {
                            channels.getIfPresent(guildId)?.send(
                                UpdateGuildAttendanceMessage(
                                    guildId,
                                    UpdateGuildAttendanceOperation.ABORT_AFTERNOON,
                                    interaction.user.id.toString()
                                )
                            )
                        }
                        interaction.componentId.contains(UpdateGuildAttendanceOperation.ABORT_EVENING.id) -> {
                            channels.getIfPresent(guildId)?.send(
                                UpdateGuildAttendanceMessage(
                                    guildId,
                                    UpdateGuildAttendanceOperation.ABORT_EVENING,
                                    interaction.user.id.toString()
                                )
                            )
                        }
                        interaction.componentId.contains(UpdateGuildAttendanceOperation.REGISTER_EVENING.id) -> {
                            channels.getIfPresent(guildId)?.send(
                                UpdateGuildAttendanceMessage(
                                    guildId,
                                    UpdateGuildAttendanceOperation.REGISTER_EVENING,
                                    interaction.user.id.toString()
                                )
                            )
                        }
                        interaction.componentId.contains(UpdateGuildAttendanceOperation.REGISTER_AFTERNOON.id) -> {
                            channels.getIfPresent(guildId)?.send(
                                UpdateGuildAttendanceMessage(
                                    guildId,
                                    UpdateGuildAttendanceOperation.REGISTER_AFTERNOON,
                                    interaction.user.id.toString()
                                )
                            )
                        }
                    }
                    interaction.deferEphemeralResponse().respond(
                        createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                    )
                } catch (e: Exception) {
                    try {
                        val response = interaction.deferEphemeralResponse()
                        when(e) {
                            is NoActiveCharacterException -> {
                                response.respond(
                                    createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                                )
                            }
                            else -> {
                                kord.getChannelOfTypeOrDefault(guildId, Channels.LOG_CHANNEL, cacheManager).createMessage(
                                    e.stackTraceToString()
                                )
                                response.respond(
                                    createGenericEmbedError(DailyAttendanceLocale.CANNOT_REGISTER.locale(locale))
                                )
                            }
                        }
                    } catch (_: Exception) {
                        if(e !is NoActiveCharacterException) {
                            kord.getChannelOfTypeOrDefault(guildId, Channels.LOG_CHANNEL, cacheManager)
                                .createMessage(e.stackTraceToString())
                        }
                    }

                }
            }
        }
    }

    private suspend fun resetPreviousInteraction(guild: Guild) {
        try {
            val lastAttendance = db.utilityScope.getLastAttendance(guild.id.toString())
                ?: throw Exception("Attendance not found")
            val channel = kord.getChannelOfType(guild.id, Channels.ATTENDANCE_CHANNEL, cacheManager)
            channel.getMessage(Snowflake(lastAttendance.message)).edit {
                components = mutableListOf()
            }
        } catch (_: Exception) {
            logger.info("Cannot delete previous attendance message for guild ${guild.name}")
        }
    }

    override fun register() {
        handleRegistration()
        launchChannelDispatcher()
        Timer(eventId).schedule(
            getStartingInstantOnNextDay(0, 0, 0).also {
                logger.info { "$eventId will start on $it"  }
            },
            24 * 60 * 60 * 1000
        ) {
            runBlocking {
                kord.guilds.collect {
                    if (cacheManager.getConfig(it.id).eventChannels[eventId]?.enabled == true) {
                        channelConsumers.get(it.id) { guildId -> launchGuildDispatcher(guildId) }
                        val reportDate = Calendar.getInstance().time
                        val locale = it.preferredLocale.language
                        val message = kord.getChannelOfType(it.id, Channels.ATTENDANCE_CHANNEL, cacheManager).createMessage(
                            prepareMessage(locale, it.id, true)
                        )
                        resetPreviousInteraction(it)
                        db.utilityScope.updateAttendance(
                            it.id.toString(),
                            AttendanceReport(
                                reportDate,
                                message.id.toString()
                            )
                        )
                    }
                }
            }
        }
    }

}