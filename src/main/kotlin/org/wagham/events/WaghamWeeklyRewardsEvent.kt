package org.wagham.events

import dev.inmo.krontab.doInfinity
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.wagham.components.CacheManager
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.CharacterStatus
import org.wagham.annotations.BotEvent
import org.wagham.config.Channels
import org.wagham.db.enums.TransactionType
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Announcement
import org.wagham.db.models.AnnouncementType
import org.wagham.db.models.Character
import org.wagham.db.models.Item
import org.wagham.db.models.embed.Transaction
import org.wagham.utils.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

@BotEvent("wagham")
class WaghamWeeklyRewardsEvent(
	override val kord: Kord,
	override val db: KabotMultiDBClient,
	override val cacheManager: CacheManager
) : Event {

	override val eventId = "wagham_weekly_rewards"
	private val taskExecutorScope = CoroutineScope(Dispatchers.Default)

	private val logger = KotlinLogging.logger {}

	companion object {
		private val tierRewards = mapOf("1" to 20, "2" to 40, "3" to 100, "4" to 200, "5" to 400)
		private val guildRewards = mapOf("1" to 10f, "2" to 20f, "3" to 50f, "4" to 100f, "5" to 200f)

		private data class Reward(
			val announcementType: AnnouncementType? = null,
			val announcement: Pair<String, Announcement>? = null,
			val items: Map<String, Int> = emptyMap(),
			val money: Float = 0f
		)

		private data class RewardsLog(
			val weekStart: Date,
			val weekEnd: Date,
			val tBadge: Int,
			val delegates: Map<String, Int> = emptyMap(),
			val playerRewards: Map<String, Reward> = emptyMap()
		) {

			fun rewardsMessage() = buildString {
				val dateFormatter = SimpleDateFormat("dd/MM")
				append("**Premi della settimana dal ${dateFormatter.format(weekStart)} al ${dateFormatter.format(weekEnd)}**\n\n")
				append("\n**Stipendi Delegati**\n")
				delegates.entries.forEach {
					val (playerId, characterName) = it.key.split(":")
					append("($characterName) <@!${playerId}>: ${it.value}\n")
				}
				append("\n**Premi Competenze**\n")
				playerRewards.entries.forEach{ (characterId, reward) ->
					val (playerId, characterName) = characterId.split(":")
					append("$characterName - (<@!$playerId>)\n")
					append("\t*MO Totali: ${reward.money}*\n")
					append("\tOggetti: ")
					append(reward.items.entries.joinToString(", "){ "${it.key} x${it.value}" })
					append("\n")
				}
				append("\n")
			}

		}
	}

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
							character.id to (tierRewards.getValue(tier))
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
			.filter { character ->
				character.hasActivityInLast30Days()
			}

	private fun Map<Pair<String, String>, List<Item>>.getRecipesForTierAndTools(tier: String, tools: List<String>, probability: Double): List<Item> =
		tools.flatMap {
			get(Pair(tier, it)) ?: emptyList()
		}.let { items ->
			when {
				probability >= 1.0 -> items.shuffled().take(probability.toInt())
				probability >= Random.nextDouble() -> items.shuffled().take(1)
				else -> emptyList()
			}
		}

	private suspend fun giveRewards(guildId: Snowflake) {
		val (craftTools, otherTools) = db.proficiencyScope.getToolProficiencies(guildId.toString()).toList().partition {
			it.labels.any { l -> l.id == CRAFT_TOOL_LABEL_ID }
		}

		val recipe = db.labelsScope.getLabel(guildId.toString(), RECIPE_LABEL_ID)?.toLabelStub()
			?: throw IllegalStateException("No recipe label found")
		val recipesByTier = db.itemsScope.getItems(guildId.toString(), listOf(recipe)).toList()
			.groupBy { item ->
				val craft = item.labels.firstOrNull { label ->
					craftTools.map { it.name }.contains(label.name)
				}?.name ?: "UNKNOWN"
				when {
					item.labels.any { it.name == "T1" } -> Pair("1", craft)
					item.labels.any { it.name == "T2" } -> Pair("2", craft)
					item.labels.any { it.name == "T3" } -> Pair("3", craft)
					item.labels.any { it.name == "T4" } -> Pair("4", craft)
					else -> Pair("UNKNOWN", craft)
				}
			}.filterKeys {
				it.first != "UNKNOWN"
			}

		// Gets the prerequisite data
		val (weekStart, weekEnd) = getWeekStartAndEnd()
		val rewardsLog = RewardsLog(
			weekStart,
			weekEnd,
			70,
			getDelegateRewards(guildId)
		)
		val expTable = cacheManager.getExpTable(guildId)


		// For each active player
		val updatedLog = getAllEligibleCharacters(guildId)
			.fold(rewardsLog) { log, character ->
				// Calculates the proficiencies rewards
				val tier = expTable.expToTier(character.ms().toFloat()).toInt()
				val tools = character.proficiencies.filter { tool ->
					craftTools.any { it.id == tool.id }
				}.map { it.name }
				val recipeRewards = tierRewards.keys.flatMap {
					when {
						tier.coerceAtMost(4) == it.toInt() -> recipesByTier.getRecipesForTierAndTools(it, tools, min(ceil(tools.size / 2.0), 6.0))
						tier > it.toInt() -> recipesByTier.getRecipesForTierAndTools(it, tools, (2.0.pow(it.toInt()-tier)))
						else -> emptyList()
					}
				}.associate{ it.name to 1 }

				val nonCraftTools = character.proficiencies.count { tool ->
					otherTools.any { it.id == tool.id }
				}
				val moneyReward = guildRewards.getValue("$tier") * (1 + nonCraftTools)

				val rewards = Reward(null, null, recipeRewards, moneyReward)
				log.copy(
					playerRewards = log.playerRewards + (character.id to rewards)
				)
			}

		val transactionResult = db.transaction(guildId.toString()) { session ->
			getAllEligibleCharacters(guildId).collect { character ->
				val moneyToGive = (updatedLog.delegates[character.id]?.toFloat() ?: 0f) + (updatedLog.playerRewards[character.id]?.money ?: 0f)
				if(moneyToGive > 0f) {
					db.charactersScope.addMoney(session, guildId.toString(), character.id, moneyToGive)
				}

				val itemsToGive = updatedLog.playerRewards[character.id]?.items ?: emptyMap()
				itemsToGive.entries.forEach { (item, qty) ->
					db.charactersScope.addItemToInventory(session, guildId.toString(), character.id, item, qty)
				}

				val itemsForTransaction = itemsToGive.mapValues { it.value.toFloat() } +
					(mapOf(transactionMoney to moneyToGive).takeIf { moneyToGive > 0f } ?: emptyMap())

				db.characterTransactionsScope.addTransactionForCharacter(
					session,
					guildId.toString(),
					character.id,
					Transaction(
						Date(), null, "REWARDS", TransactionType.ADD, itemsForTransaction
					)
				)
			}
		}
		getChannelOfType(guildId, Channels.LOG_CHANNEL).sendTextMessage(buildString {
			if (transactionResult.committed) append("Successfully assigned everything")
			else append("Error ${transactionResult.exception?.stackTraceToString()}")
		})
		getChannelOfType(guildId, Channels.MESSAGE_CHANNEL).sendTextMessage("Dlin-Dlon! TBadge, premi master e stipendi sono stati assegnati! Godetevi le vostre ricchezze, maledetti! :moneybag:")
		getChannelOfType(guildId, Channels.BOT_CHANNEL).sendTextMessage(updatedLog.rewardsMessage())
	}

	override fun register() {
		runBlocking {
			kord.guilds.collect { guild ->
				if (cacheManager.getConfig(guild.id).eventChannels[eventId]?.enabled == true) {
					taskExecutorScope.launch {
						val schedulerConfig = "0 0 18 * * ${getTimezoneOffset()}o 0w"
						logger.info { "Starting Weekly Rewards for guild ${guild.name} at $schedulerConfig" }
						doInfinity(schedulerConfig) {
							giveRewards(guild.id)
						}
					}
				}
			}
		}
	}
}