package org.wagham.events

import dev.inmo.krontab.doInfinity
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Member
import dev.kord.core.supplier.EntitySupplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.wagham.annotations.BotEvent
import org.wagham.components.CacheManager
import org.wagham.config.Channels
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.CharacterStatus
import org.wagham.db.models.Character
import org.wagham.entities.channels.RegisteredSession
import org.wagham.utils.getChannelOfTypeOrDefault
import org.wagham.utils.sendTextMessage

@BotEvent("wagham")
class WaghamCheckTierEvent(
	override val kord: Kord,
	override val db: KabotMultiDBClient,
	override val cacheManager: CacheManager
) : RestrictedGuildEvent() {

	override val eventId = "register_session_check_tier"
	private val taskExecutorScope = CoroutineScope(Dispatchers.Default)
	private var channelDispatcher: Job? = null
	private val logger = KotlinLogging.logger {}
	private val singleInsults = listOf("quel maramaldo", "quello zozzone", "quel maleducato", "quel maledetto", "quel manigoldo",
		"quel masnadiero", "quel pusillanime", "quell'infingardo", "quello svergognato", "quello scostumato", "quel mentecatto",
		"quell'illetterato", "quel furfante", "quell'onanista")

	/**
	 * Given a user in a guild, it gets all the active [Character]s for that user and ensures that the user has all
	 * the tier roles associated to all their characters.
	 *
	 * @param player a kord [Member].
	 * @param guildId the id of the guild.
	 * @param supplier an [EntitySupplier].
	 */
	private suspend fun updateUserRoles(player: Member, guildId: Snowflake, supplier: EntitySupplier) {
		val expTable = cacheManager.getExpTable(guildId)
		val tiers = db.charactersScope.getActiveCharacters(guildId.toString(), player.id.toString()).map {
			expTable.expToTier(it.ms().toFloat())
		}
		val updatedRoles = player.roles.filter { !Regex("Tier [0-9].*").matches(it.name) }.map { it.id }.toList() +
			tiers.map { tier -> supplier.getGuildRoles(guildId).first { it.name.matches("Tier $tier .*".toRegex()) }.id }.toList()
		player.edit {
			roles = updatedRoles.toMutableSet()
		}
	}

	/**
	 * For each character, it checks if the corresponding user exists and has the correct role.
	 * If a user does not exist, then it means that it left the server so the bot sends a log in the log or default
	 * channel.
	 * If a user does not have the correct role, then the role is updated and a funny message is sent on the main
	 * message or default channel.
	 *
	 * @param guildId the guild to check.
	 * @param characters a [List] of [Character] whose users must be updated.
	 */
	private suspend fun updateTierTag(guildId: Snowflake, characters: List<Character>) {
		characters.fold(Pair(emptyList<Pair<Member, Character>>(), emptyList<Character>())) { acc, character ->
			val tier = cacheManager.getExpTable(guildId).expToTier(character.ms().toFloat())
			val player = kord.defaultSupplier.getMemberOrNull(guildId, Snowflake(character.player))
			if (player == null) {
				acc.copy(second = acc.second + character)
			} else if (player.roles.toList().none { Regex("Tier $tier.*").matches(it.name) }) {
				acc.copy(first = acc.first + (player to character))
			} else acc
		}.let { updates ->
			if ( updates.second.isNotEmpty()) {
				kord.getChannelOfTypeOrDefault(guildId, Channels.LOG_CHANNEL, cacheManager).sendTextMessage(
					"WARNING: The following users do not exist\n${
						updates.second.joinToString(separator = "") { "(<@!${it.player}>) - ${it.name}\n" }
					}"
				)
			}
			updates.first
		}.let { updates ->
			updates.map { it.first }.toSet().forEach {
				updateUserRoles(it, guildId, kord.defaultSupplier)
			}
			if (updates.size == 1) {
				"Dlin Dlon! ${singleInsults.random().replaceFirstChar { it.uppercase() }} di ${updates.first().second.name} (<@!${updates.first().first.id}>) è salito di tier. Congratulazioni!"
			} else if (updates.size > 1) {
				val concatenated = updates.subList(0, updates.size-1).joinToString { "${singleInsults.random()} di ${it.second.name} (<@!${it.first.id}>)" } +
					" e ${singleInsults.random()} di ${updates.last().second.name} (<@!${updates.last().first.id}>)"
				"Dlin Dlon! ${concatenated.replaceFirstChar { it.uppercase() }} sono saliti di tier. Congratulazioni!"
			} else null
		}?.let { message ->
			kord.getChannelOfTypeOrDefault(guildId, Channels.MESSAGE_CHANNEL, cacheManager).sendTextMessage(message)
		}
	}

	/**
	 * Given a [RegisteredSession] message, checks that all the users associated to the characters in the sessions have
	 * the appropriate roles.
	 *
	 * @param registeredSession a [RegisteredSession] message.
	 */
	private suspend fun updateRoles(registeredSession: RegisteredSession) {
		db.sessionScope.getSessionById(registeredSession.guildId, registeredSession.sessionId)?.let { session ->
			val activeCharacters = db.charactersScope.getCharacters(
				registeredSession.guildId,
				session.characters.map { it.character }
			).filter {
				it.status == CharacterStatus.active
			}.toList()
			updateTierTag(Snowflake(registeredSession.guildId), activeCharacters)
		}
	}

	/**
	 * Waits for message to be published to the channel for the class [WaghamCheckTierEvent] and with message type
	 * [RegisteredSession], then handles the message.
	 */
	private fun launchChannelDispatcher() = taskExecutorScope.launch {
		try {
			val channel = cacheManager.getChannel<WaghamCheckTierEvent, RegisteredSession>()
			for(message in channel) {
				updateRoles(message)
			}
		} catch (e: Exception) {
			logger.info { "Error while dispatching tier updates op: ${e.stackTraceToString()}" }
		}
	}

	/**
	 * Registers the [Event].
	 * It launches a coroutine that registers to the appropriate channels and periodically checks if it is active.
	 */
	override fun register() {
		launchChannelDispatcher()
		taskExecutorScope.launch {
			doInfinity("0 * * * *") {
				if (channelDispatcher?.isActive != true) {
					logger.info { "Dispatcher is dead, relaunching" }
					channelDispatcher = launchChannelDispatcher()
				}
			}
		}
	}
}