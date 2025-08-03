package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.embed
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.NoShowCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.subcommands.NoShowListLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.utils.defaultLocale
import org.wagham.utils.extractCommonParameters
import java.text.SimpleDateFormat
import kotlin.collections.component1
import kotlin.collections.component2

@BotSubcommand("all", NoShowCommand::class)
class NoShowList(
	override val kord: Kord,
	override val db: KabotMultiDBClient,
	override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

	override val commandName = "list"
	override val defaultDescription = NoShowListLocale.DESCRIPTION.locale(defaultLocale)
	override val localeDescriptions: Map<Locale, String> = NoShowListLocale.DESCRIPTION.localeMap
	private val formatter = SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())

	override suspend fun registerCommand() {}

	override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
		localeDescriptions.forEach{ (locale, description) ->
			description(locale, description)
		}
		user("target", NoShowListLocale.TARGET.locale("en")) {
			NoShowListLocale.TARGET.localeMap.forEach{ (locale, description) ->
				description(locale, description)
			}
			required = true
			autocomplete = true
		}
	}

	override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
		val params = event.extractCommonParameters()
		val target = event.interaction.command.users["target"] ?: throw IllegalStateException("Target not found")
		val player = db.playersScope.getPlayer(params.guildId.toString(), target.id.toString())
			?: throw IllegalStateException("Player ${target.id} not found")
		return if (player.strikes.isNotEmpty()) {
			fun InteractionResponseModifyBuilder.() {
				embed {
					title = "${NoShowListLocale.TITLE.locale(params.locale)}${target.username}"
					color = Colors.DEFAULT.value
					player.strikes.sortedBy {
						it.date
					}.reversed().forEach {
						field {
							name = it.title
							value = formatter.format(it.date)
							inline = true
						}
					}
				}
			}
		} else {
			fun InteractionResponseModifyBuilder.() {
				embed {
					color = Colors.DEFAULT.value
					title = "${NoShowListLocale.TITLE.locale(params.locale)}${target.username}"
					description = NoShowListLocale.NO_STRIKES.locale(params.locale)
				}
			}
		}
	}

	override suspend fun handleResponse(
		builder: InteractionResponseModifyBuilder.() -> Unit,
		event: GuildChatInputCommandInteractionCreateEvent
	) { }
}