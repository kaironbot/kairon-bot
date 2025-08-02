package org.wagham.events

import dev.kord.core.Kord
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import org.wagham.annotations.BotEvent
import org.wagham.components.CacheManager
import org.wagham.config.Channels
import org.wagham.db.KabotMultiDBClient
import org.wagham.utils.getChannelOfType
import org.wagham.utils.latestStrike
import org.wagham.utils.recentStrikes
import org.wagham.utils.sendTextMessage
import java.time.Instant
import java.time.temporal.ChronoUnit

@BotEvent("all")
class BanCheckEvent(
	override val kord: Kord,
	override val db: KabotMultiDBClient,
	override val cacheManager: CacheManager
) : RestrictedGuildEvent() {

	override val eventId = "ban_check_event"

	override fun register() {
		kord.on<MessageCreateEvent> {
			if (guildId != null && isAllowed(guildId, message)) {
				val now = Instant.now()
				val bannedPlayers = message.mentionedUserIds.mapNotNull {
					db.playersScope.getPlayer(guildId.toString(), it.toString())
				}.filter {
					it.recentStrikes.size >= 3 &&
						ChronoUnit.DAYS.between(it.latestStrike.date.toInstant(), now) <= 15
				}
				if (bannedPlayers.isNotEmpty()) {
					getChannelOfType(guildId!!, Channels.MASTER_CHANNEL).sendTextMessage(
						"""
							<@&1099401653192495164> <@&1102233358378991727> I seguenti giocatori sono stati chiamati anche
							nonostante i 3 strike attivi: ${bannedPlayers.joinToString(" ") { "<@${it.playerId}>" }}
						""".trimIndent()
					)
				}
			}
		}
	}

}