package org.wagham.events

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.channel.GuildMessageChannel
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
import org.wagham.exceptions.ChannelNotFoundException
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.createGenericEmbedError
import org.wagham.utils.createGenericEmbedSuccess
import org.wagham.utils.daysToToday
import org.wagham.utils.getStartingInstantOnNextDay
import java.util.*
import kotlin.concurrent.schedule

@BotEvent("all")
class DailyAttendanceEvent(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : Event {

    override val eventId = "daily_attendance"
    private val logger = KotlinLogging.logger {}

    private suspend fun getAttendanceChannel(guildId: Snowflake) =
        (cacheManager.getConfig(guildId).channels[Channels.ATTENDANCE_CHANNEL.name]
            ?: throw ChannelNotFoundException(Channels.ATTENDANCE_CHANNEL.name) )
            .let { Snowflake(it) }
            .let {
                kord.getGuildOrNull(guildId)?.getChannel(it)?.asChannelOf<GuildMessageChannel>()
                    ?: throw ChannelNotFoundException(Channels.ATTENDANCE_CHANNEL.name)
            }

    private suspend fun getLogChannel(guildId: Snowflake) =
        cacheManager.getConfig(guildId).channels[Channels.LOG_CHANNEL.name]
            ?.let { Snowflake(it) }
            ?.let {
                kord.getGuildOrNull(guildId)?.getChannel(it)?.asChannelOf<GuildMessageChannel>()
            } ?: kord.getGuildOrNull(guildId)?.getSystemChannel()
            ?: throw ChannelNotFoundException(Channels.LOG_CHANNEL.name)

    private suspend fun getAttendanceOrNull(guildId: Snowflake) =
        try {
            db.utilityScope.getTodayAttendance(guildId.toString())
        } catch (e: ResourceNotFoundException) {
            null
        }

    private suspend fun buildDescription(locale: String, guildId: Snowflake) = buildString {
        append("${DailyAttendanceLocale.DESCRIPTION.locale(locale)}\n")
        getAttendanceOrNull(guildId)
            ?.players
            ?.entries
            ?.groupBy { it.value.tier }
            ?.entries
            ?.forEach { (tier, players) ->
                append("**$tier**: ")
                players.forEach { p ->
                    append("<@${p.key}> (${p.value.daysSinceLastPlayed.takeIf { it >= 0 } ?: "-"}) ")
                }
                append("\n")
            }
    }

    private suspend fun prepareMessage(locale: String, guildId: Snowflake): UserMessageCreateBuilder.() -> Unit {
        val desc = buildDescription(locale, guildId)
        return fun UserMessageCreateBuilder.() {
            embed {
                title = DailyAttendanceLocale.TITLE.locale(locale)
                description = desc
                color = Colors.DEFAULT.value
            }
            actionRow {
                interactionButton(ButtonStyle.Primary, "${this@DailyAttendanceEvent::class.qualifiedName}-register") {
                    label = DailyAttendanceLocale.REGISTER.locale(locale)
                }
                interactionButton(ButtonStyle.Danger, "${this@DailyAttendanceEvent::class.qualifiedName}-abort") {
                    label = DailyAttendanceLocale.DEREGISTER.locale(locale)
                }
            }
        }
    }

    private fun handleRegistration() {
        kord.on<ButtonInteractionCreateEvent> {
            if(interaction.componentId.startsWith("${this@DailyAttendanceEvent::class.qualifiedName}")) {
                val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
                val guildId = interaction.data.guildId.value ?: throw GuildNotFoundException()
                val channel = getAttendanceChannel(guildId)
                val expTable = cacheManager.getExpTable(guildId)
                val currentAttendance = db.utilityScope.getTodayAttendance(guildId.toString())
                try {
                    when {
                        interaction.componentId.endsWith("register") -> {
                            val character = db.charactersScope.getActiveCharacter(guildId.toString(), interaction.user.id.toString())
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
                        interaction.componentId.endsWith("abort") -> {
                            val character = db.charactersScope.getActiveCharacter(guildId.toString(), interaction.user.id.toString())
                            db.utilityScope.updateAttendance(
                                guildId.toString(),
                                currentAttendance.copy(
                                    players = currentAttendance.players - character.player
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
                                getLogChannel(guildId).createMessage(
                                    e.stackTraceToString()
                                )
                                response.respond(
                                    createGenericEmbedError(DailyAttendanceLocale.CANNOT_REGISTER.locale(locale))
                                )
                            }
                        }
                    } catch (_: Exception) {
                        if(e !is NoActiveCharacterException) {
                            getLogChannel(guildId).createMessage(e.stackTraceToString())
                        }

                    }

                }
            }
        }
    }

    override fun register() {
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
                        val locale = it.preferredLocale.language
                        val message = getAttendanceChannel(it.id).createMessage(
                            prepareMessage(locale, it.id)
                        )
                        db.utilityScope.updateAttendance(
                            it.id.toString(),
                            AttendanceReport(
                                dateAtMidnight(Calendar.getInstance().time),
                                message.id.toString(),
                                emptyMap()
                            )
                        )
                    }
                }
            }
        }
    }

}