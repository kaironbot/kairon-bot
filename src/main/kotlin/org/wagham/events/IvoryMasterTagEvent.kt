package org.wagham.events

import dev.inmo.krontab.doInfinity
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.edit
import dev.kord.core.event.guild.MemberUpdateEvent
import dev.kord.core.on
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.wagham.annotations.BotEvent
import org.wagham.components.CacheManager
import org.wagham.config.Channels
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.CharacterStatus
import org.wagham.utils.daysToToday
import org.wagham.utils.getChannelOfType
import org.wagham.utils.getTimezoneOffset
import org.wagham.utils.sendTextMessage
import java.util.*
import kotlin.math.min

@BotEvent("wagham")
class IvoryMasterTagEvent (
	override val kord: Kord,
	override val db: KabotMultiDBClient,
	override val cacheManager: CacheManager
): Event {

	private val ivoryGuildId = Snowflake(1099390660672503980)
	override val eventId = "ivory_master_tag"
	private val logger = KotlinLogging.logger {}
	private val taskExecutorScope = CoroutineScope(Dispatchers.Default)
	private val message = """Ciao!
		Sei diventato inattivo come master nel server di Tales from Ivory. Il tag master è stato rimosso. Se vorrai potrai recuperare il materiale che hai prodotto, tuttavia la tua istanza foundry al bisogno potrà essere riassegnata. 
		Questo non ti farà perdere i dati che hai creato, ma ti impedirà di accedere alla partita che ti è stata assegnata solo in caso verrà riassegnata a un altro master.
		Potrai richiedere il tag quando lo vorrai e rincominciare a masterare, tuttavia, se tornerai nuovamente inattivo, riavere il ruolo di master potrebbe non essere disponibile.
	""".trimIndent()
	private val playerMessage = """Ciao!
		Sei diventato inattivo come giocatore nel server di Tales from Ivory perchè non giochi o masteri da più di 3 mesi, quindi ti sono stati tolti i permessi. 
		Se vuoi ricominciare a giocare, contatta l'accoglienza nel canale di benvenuto.
	""".trimIndent()

	private suspend fun getAllMasters(guild: Snowflake) = kord.getGuild(guild).members.filter { user ->
			user.roles.firstOrNull {
				it.name == "Master"
			} != null
		}

	private suspend fun checkMasterInactivity(guild: Snowflake) = getAllMasters(guild).filter { user ->
			db.playersScope.getPlayer(guild.toString(), user.id.toString()).let {
				it?.masterSince == null || daysToToday(it.masterSince!!) > 30
			} && db.charactersScope.getCharacters(guild.toString(), user.id.toString()).firstOrNull {
				it.lastMastered != null && daysToToday(it.lastMastered!!) <= 30
			} == null
		}.collect { user ->
			user.edit {
				roles = user.roles.filter { it.name != "Master" }.map { it.id }.toList().toMutableSet()
			}
			user.getDmChannel().sendTextMessage(message)
			getChannelOfType(Snowflake(1099390660672503980), Channels.LOG_CHANNEL)
				.sendTextMessage("Removed master tag from ${user.username}")
		}

	private suspend fun getAllPlayers(guild: Snowflake) = kord.getGuild(guild).members.filter { user ->
		user.roles.toList().let { roles ->
			roles.none { it.name == "Admin" || it.name == "Moderazione" || it.name == "Delegato" } && roles.any { it.name == "Giocatore" }
		} && !user.isBot
	}

	private suspend fun checkPlayerInactivity(guild: Snowflake) = getAllPlayers(guild).filter { user ->
		db.charactersScope.getActiveCharacters(guild.toString(), user.id.toString()).map { character ->
			val created = character.created?.let { daysToToday(it) } ?: 100
			val lastPlayed = character.lastPlayed?.let { daysToToday(it) } ?: 100
			val lastMastered = character.lastMastered?.let { daysToToday(it) } ?: 100
			min(min(created, lastPlayed), lastMastered) <= 90
		}.toList().none { it }
	}.collect { user ->
		try {
			user.edit {
				roles = mutableSetOf(Snowflake("1102911597174853763"))
			}
			user.getDmChannel().sendTextMessage(playerMessage)
			db.charactersScope.getActiveCharacters(guild.toString(), user.id.toString()).collect { character ->
				db.charactersScope.updateCharacter(
					guild.toString(),
					character.copy(status = CharacterStatus.retired)
				)
			}
			getChannelOfType(Snowflake(1099390660672503980), Channels.LOG_CHANNEL)
				.sendTextMessage("Removed player tag from ${user.username}")
		} catch(e: Exception) {
			logger.error { e.stackTraceToString() }
			getChannelOfType(Snowflake(1099390660672503980), Channels.LOG_CHANNEL)
				.sendTextMessage("Error while removing player tag from ${user.username}")
		}
	}

	override fun register() {
		taskExecutorScope.launch {
			val schedulerConfig = "0 0 0 * * ${getTimezoneOffset()}o"
			logger.info { "Starting Inactivity check at $schedulerConfig" }
			doInfinity(schedulerConfig) {
				try {
					checkMasterInactivity(ivoryGuildId)
					checkPlayerInactivity(ivoryGuildId)
				} catch (e: Exception) {
					logger.info { "Error while dispatching drop op: ${e.stackTraceToString()}" }
					getChannelOfType(Snowflake(1099390660672503980), Channels.LOG_CHANNEL)
						.sendTextMessage("Error while dispatching drop op: ${e.stackTraceToString()}")
				}
			}
		}

		kord.on<MemberUpdateEvent> {
			try {
				old?.also { oldMember ->
					val hasMasterRole = member.roles.firstOrNull { it.name == "Master" } != null
					val hadMasterRole = oldMember.roles.firstOrNull { it.name == "Master" } != null
					if (!hadMasterRole && hasMasterRole) {
						db.playersScope.setMasterDate(ivoryGuildId.toString(), member.id.toString(), Date())
					} else if (!hasMasterRole && hadMasterRole) {
						db.playersScope.setMasterDate(ivoryGuildId.toString(), member.id.toString(), null)
					}
				}
			} catch (e: Exception) {
				logger.error { "Something went wrong while updating master date:\n${e.stackTraceToString()}" }
			}
		}
	}


}