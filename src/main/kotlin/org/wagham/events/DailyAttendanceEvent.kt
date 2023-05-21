package org.wagham.events

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.message.create.UserMessageCreateBuilder
import dev.kord.rest.builder.message.create.actionRow
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
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
import org.wagham.db.exceptions.ResourceNotFoundException
import org.wagham.db.models.AttendanceReport
import org.wagham.db.models.embed.AttendanceReportPlayer
import org.wagham.db.utils.dateAtMidnight
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.*
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule

@BotEvent("all")
class DailyAttendanceEvent(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : Event, Identifiable {

    override val eventId = "daily_attendance"
    private val logger = KotlinLogging.logger {}
    private val interactionCache: Cache<Snowflake, String> = Caffeine.newBuilder()
        .expireAfterWrite(2, TimeUnit.DAYS)
        .build()

    private suspend fun getAttendanceOrNull(guildId: Snowflake) =
        try {
            db.utilityScope.getTodayAttendance(guildId.toString())
        } catch (e: ResourceNotFoundException) {
            null
        }

    private fun Map<String, AttendanceReportPlayer>.appendList(buildCtx: StringBuilder) = entries
        .groupBy { it.value.tier }.entries
        .sortedBy { it.key }
        .forEach { (tier, players) ->
            buildCtx.append("**$tier**: ")
            players.forEach { p ->
                buildCtx.append("<@${p.key}> (${p.value.daysSinceLastPlayed.takeIf { it >= 0 } ?: "-"}) ")
            }
            buildCtx.append("\n")
        }

    private suspend fun buildDescription(locale: String, guildId: Snowflake) = buildString {
        append("${DailyAttendanceLocale.DESCRIPTION.locale(locale)}\n")
        append("*${DailyAttendanceLocale.LAST_ACTIVITY.locale(locale)}*\n\n")
        getAttendanceOrNull(guildId)?.let { report ->
            append("**${DailyAttendanceLocale.AFTERNOON_AVAILABILITY.locale(locale)}**\n")
            report.afternoonPlayers.appendList(this)
            append("\n")
            append("**${DailyAttendanceLocale.EVENING_AVAILABILITY.locale(locale)}**\n")
            report.players.appendList(this)
        }
    }

    private suspend fun prepareMessage(locale: String, guildId: Snowflake): UserMessageCreateBuilder.() -> Unit {
        val desc = buildDescription(locale, guildId)
        val interactionId = interactionCache.getIfPresent(guildId)
            ?: throw IllegalStateException("Attendance not initialized")
        return fun UserMessageCreateBuilder.() {
            embed {
                title = DailyAttendanceLocale.TITLE.locale(locale)
                description = desc
                color = Colors.DEFAULT.value
            }
            actionRow {
                interactionButton(ButtonStyle.Primary, buildElementId("register", "afternoon", interactionId)) {
                    label = DailyAttendanceLocale.REGISTER_AFTERNOON.locale(locale)
                }
                interactionButton(ButtonStyle.Primary, buildElementId("register", "evening", interactionId)) {
                    label = DailyAttendanceLocale.REGISTER_EVENING.locale(locale)
                }
                interactionButton(ButtonStyle.Danger, buildElementId("abort", "afternoon", interactionId)) {
                    label = DailyAttendanceLocale.DEREGISTER_AFTERNOON.locale(locale)
                }
                interactionButton(ButtonStyle.Danger, buildElementId("abort", "evening", interactionId)) {
                    label = DailyAttendanceLocale.DEREGISTER_EVENING.locale(locale)
                }
            }
        }
    }

    private fun handleRegistration() {
        kord.on<ButtonInteractionCreateEvent> {
            val guildId = interaction.data.guildId.value ?: throw GuildNotFoundException()
            val interactionId = interactionCache.getIfPresent(guildId)
            if(interaction.componentId.startsWith("${this@DailyAttendanceEvent::class.qualifiedName}")
                && interactionId != null
                && interaction.componentId.endsWith(interactionId)) {
                val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
                val channel = kord.getChannelOfType(guildId, Channels.ATTENDANCE_CHANNEL, cacheManager)
                val expTable = cacheManager.getExpTable(guildId)
                val currentAttendance = db.utilityScope.getTodayAttendance(guildId.toString())
                try {
                    val character = db.charactersScope.getActiveCharacter(guildId.toString(), interaction.user.id.toString())
                    when {
                        interaction.componentId.contains("abort-afternoon") -> {
                            db.utilityScope.updateAttendance(
                                guildId.toString(),
                                currentAttendance.copy(
                                    afternoonPlayers = currentAttendance.afternoonPlayers - character.player
                                )
                            )
                        }
                        interaction.componentId.contains("abort-evening") -> {
                            db.utilityScope.updateAttendance(
                                guildId.toString(),
                                currentAttendance.copy(
                                    players = currentAttendance.players - character.player
                                )
                            )
                        }
                        interaction.componentId.contains("register-evening") && currentAttendance.players.containsKey(character.player) -> true
                        interaction.componentId.contains("register-afternoon") && currentAttendance.afternoonPlayers.containsKey(character.player) -> true
                        interaction.componentId.contains("register-evening") -> {
                            db.utilityScope.updateAttendance(
                                guildId.toString(),
                                currentAttendance.copy(
                                    players = currentAttendance.players +
                                        (character.player to AttendanceReportPlayer(
                                            character.lastPlayed?.let(::daysToToday) ?: -1,
                                            expTable.expToTier(character.ms().toFloat())
                                        ))
                                )
                            )
                        }
                        interaction.componentId.contains("register-afternoon") -> {
                            db.utilityScope.updateAttendance(
                                guildId.toString(),
                                currentAttendance.copy(
                                    afternoonPlayers = currentAttendance.afternoonPlayers +
                                            (character.player to AttendanceReportPlayer(
                                                character.lastPlayed?.let(::daysToToday) ?: -1,
                                                expTable.expToTier(character.ms().toFloat())
                                            ))
                                )
                            )
                        }
                        else -> false
                    }.let { result ->
                        if(result) {
                            val desc = buildDescription(interaction.guildLocale?.language ?: "en", guildId)
                            channel.getMessage(Snowflake(currentAttendance.message)).edit {
                                embed {
                                    title = DailyAttendanceLocale.TITLE.locale(interaction.guildLocale?.language ?: "en")
                                    description = desc
                                    color = Colors.DEFAULT.value
                                }
                            }
                            interaction.deferEphemeralResponse().respond(
                                createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                            )
                        } else {
                            interaction.deferEphemeralResponse().respond(
                                createGenericEmbedError(CommonLocale.ERROR.locale(locale))
                            )
                        }
                    }
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

    override fun register() {
        runBlocking {
            kord.guilds.collect {
                try {
                    val current = db.utilityScope.getTodayAttendance(it.id.toString())
                    interactionCache.put(it.id, current.date.time.toString())
                } catch (_: Exception) {
                    logger.info { "No current attendance for guild ${it.name}" }
                }
            }
        }
        Timer(eventId).schedule(
            getStartingInstantOnNextDay(8, 0, 0).also {
                logger.info { "$eventId will start on $it"  }
            },
            24 * 60 * 60 * 1000
        ) {
            handleRegistration()
            runBlocking {
                kord.guilds.collect {
                    if (cacheManager.getConfig(it.id).eventChannels[eventId]?.enabled == true) {
                        val reportDate = dateAtMidnight(Calendar.getInstance().time)
                        val locale = it.preferredLocale.language
                        interactionCache.put(it.id, reportDate.time.toString())
                        val message = kord.getChannelOfType(it.id, Channels.ATTENDANCE_CHANNEL, cacheManager).createMessage(
                            prepareMessage(locale, it.id)
                        )
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