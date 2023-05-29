package org.wagham.events

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

    private suspend fun getAttendanceOrNull(guildId: Snowflake) =
        try {
            db.utilityScope.getLastAttendance(guildId.toString())
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

    private suspend fun buildDescription(locale: String, guildId: Snowflake, newAttendance: Boolean = false) = buildString {
        append("${DailyAttendanceLocale.DESCRIPTION.locale(locale)}\n")
        append("*${DailyAttendanceLocale.LAST_ACTIVITY.locale(locale)}*\n\n")
        getAttendanceOrNull(guildId)?.let { report ->
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

    private fun handleRegistration() {
        kord.on<ButtonInteractionCreateEvent> {
            val guildId = interaction.data.guildId.value ?: throw GuildNotFoundException()
            if(interaction.componentId.startsWith("${this@DailyAttendanceEvent::class.qualifiedName}")) {
                val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
                val channel = kord.getChannelOfType(guildId, Channels.ATTENDANCE_CHANNEL, cacheManager)
                val expTable = cacheManager.getExpTable(guildId)
                val currentAttendance = db.utilityScope.getLastAttendance(guildId.toString())
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

    private suspend fun resetPreviousInteraction(guild: Guild) {
        try {
            val lastAttendance = db.utilityScope.getLastAttendance(guild.id.toString())
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
        Timer(eventId).schedule(
            getStartingInstantOnNextDay(0, 0, 0).also {
                logger.info { "$eventId will start on $it"  }
            },
            24 * 60 * 60 * 1000
        ) {
            runBlocking {
                kord.guilds.collect {
                    if (cacheManager.getConfig(it.id).eventChannels[eventId]?.enabled == true) {
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