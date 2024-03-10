package org.wagham.events

import dev.inmo.krontab.doInfinity
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.edit
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
import org.wagham.utils.daysToToday
import org.wagham.utils.getChannelOfType
import org.wagham.utils.getTimezoneOffset
import org.wagham.utils.sendTextMessage

@BotEvent("wagham")
class IvoryMasterTagEvent (
	override val kord: Kord,
	override val db: KabotMultiDBClient,
	override val cacheManager: CacheManager
): Event {

	override val eventId = "ivory_master_tag"
	private val logger = KotlinLogging.logger {}
	private val taskExecutorScope = CoroutineScope(Dispatchers.Default)
	private val message = """Ciao!
		Sei diventato inattivo come master nel server di Tales from Ivory. Il tag master è stato rimosso. Se vorrai potrai recuperare il materiale che hai prodotto, tuttavia la tua istanza foundry al bisogno potrà essere riassegnata. 
		Questo non ti farà perdere i dati che hai creato, ma ti impedirà di accedere alla partita che ti è stata assegnata solo in caso verrà riassegnata a un altro master.
		Potrai richiedere il tag quando lo vorrai e rincominciare a masterare, tuttavia, se tornerai nuovamente inattivo, riavere il ruolo di master potrebbe non essere disponibile.
	""".trimIndent()

	private suspend fun getAllMasters(guild: Snowflake) = kord.getGuild(guild).members.filter { user ->
			user.roles.firstOrNull {
				it.name == "Master"
			} != null
		}

	private suspend fun checkMasterInactivity(guild: Snowflake) = getAllMasters(guild).filter { user ->
			db.charactersScope.getCharacters(guild.toString(), user.id.toString()).firstOrNull {
				it.lastMastered != null && daysToToday(it.lastMastered!!) <= 30
			} == null
		}.collect { user ->
			user.edit {
				roles = user.roles.filter { it.name != "Master" }.map { it.id }.toList().toMutableSet()
			}
			user.getDmChannel().sendTextMessage(message)
			getChannelOfType(Snowflake(1099390660672503980), Channels.MESSAGE_CHANNEL)
				.sendTextMessage("Removed master tag from ${user.username}")
		}

	override fun register() {
		taskExecutorScope.launch {
			val schedulerConfig = "0 0 0 * * ${getTimezoneOffset()}o"
			logger.info { "Starting Inactivity check at $schedulerConfig" }
			doInfinity(schedulerConfig) {
				try {
					checkMasterInactivity(Snowflake(1099390660672503980))
				} catch (e: Exception) {
					logger.info { "Error while dispatching drop op: ${e.stackTraceToString()}" }
					getChannelOfType(Snowflake(1099390660672503980), Channels.MESSAGE_CHANNEL)
						.sendTextMessage("Error while dispatching drop op: ${e.stackTraceToString()}")
				}
			}
		}
	}


}