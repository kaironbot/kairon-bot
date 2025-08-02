package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.User
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.NoShowCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.subcommands.NoShowStrikeLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.embed.Strike
import org.wagham.utils.createGenericEmbedSuccess
import org.wagham.utils.defaultLocale
import org.wagham.utils.extractCommonParameters
import org.wagham.utils.latestStrike
import org.wagham.utils.recentStrikes
import org.wagham.utils.sendTextMessage
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.component1
import kotlin.collections.component2

@BotSubcommand("all", NoShowCommand::class)
class NoShowStrike(
	override val kord: Kord,
	override val db: KabotMultiDBClient,
	override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

	override val commandName = "strike"
	override val defaultDescription = NoShowStrikeLocale.DESCRIPTION.locale(defaultLocale)
	override val localeDescriptions: Map<Locale, String> = NoShowStrikeLocale.DESCRIPTION.localeMap
	private val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

	override suspend fun registerCommand() {}

	override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
		localeDescriptions.forEach{ (locale, description) ->
			description(locale, description)
		}
		user("target", NoShowStrikeLocale.TARGET.locale("en")) {
			NoShowStrikeLocale.TARGET.localeMap.forEach{ (locale, description) ->
				description(locale, description)
			}
			required = true
			autocomplete = true
		}
		string("session", NoShowStrikeLocale.SESSION.locale("en")) {
			NoShowStrikeLocale.SESSION.localeMap.forEach{ (locale, description) ->
				description(locale, description)
			}
			required = true
		}
	}

	private suspend fun sendWarningMessageIfNeeded(user: User, guildId: Snowflake, locale: String) {
		val player = db.playersScope.getPlayer(guildId.toString(), user.id.toString())
			?: throw IllegalStateException("Player not found")
		if (player.recentStrikes.size >= 3) {
			user.getDmChannel().sendTextMessage(buildString {
				append(NoShowStrikeLocale.STRIKE_RECEIVED.locale(locale))
				append(player.latestStrike.title)
				append("\n")
				append(NoShowStrikeLocale.CURRENT_STRIKES.locale(locale))
				append("\n")
				player.recentStrikes.forEach {
					append("${it.title} - ${formatter.format(it.date.toInstant())}\n")
				}
				append(NoShowStrikeLocale.STRIKE_EXPIRATION.locale(locale))
				append("\n")
				append(NoShowStrikeLocale.BANNED_UNTIL.locale(locale))
				append(formatter.format(LocalDate.from(player.latestStrike.date.toInstant()).plusDays(15)))
			})
		}

	}

	override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
		val params = event.extractCommonParameters()
		val session = event.interaction.command.strings["session"] ?: throw IllegalStateException("Session not found")
		val target = event.interaction.command.users["target"] ?: throw IllegalStateException("Target not found")
		db.playersScope.addStrike(
			guildId = params.guildId.toString(),
			playerId = target.id.toString(),
			strike = Strike(
				date = Date.from(Instant.now()),
				title = session
			)
		).also {
			if(!it) {
				throw IllegalStateException("Cannot update player ${target.id}")
			}
		}
		sendWarningMessageIfNeeded(target, params.guildId, params.locale)
		return createGenericEmbedSuccess(NoShowStrikeLocale.SUCCESS.locale(params.locale))
	}

	override suspend fun handleResponse(
		builder: InteractionResponseModifyBuilder.() -> Unit,
		event: GuildChatInputCommandInteractionCreateEvent
	) { }
}