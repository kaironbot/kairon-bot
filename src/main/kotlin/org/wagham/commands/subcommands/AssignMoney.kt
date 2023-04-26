package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.number
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.AssignCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.commands.PayLocale
import org.wagham.config.locale.subcommands.AssignMoneyLocale
import org.wagham.config.locale.subcommands.MasterStatsLocale
import org.wagham.db.KabotMultiDBClient

@BotSubcommand("all", AssignCommand::class)
class AssignMoney(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "money"
    override val defaultDescription = "Assign money to a player"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Assign money to a player",
        Locale.ITALIAN to "Assegna delle monete a un giocatore"
    )
    private val additionalUsers: Int = 5

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        number("amount", AssignMoneyLocale.AMOUNT.locale("en")) {
            AssignMoneyLocale.AMOUNT.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
        user("target", AssignMoneyLocale.TARGET.locale("en")) {
            AssignMoneyLocale.TARGET.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = false
            autocomplete = true
        }
        (1 .. additionalUsers).forEach { paramIndex ->
            user("target-$paramIndex", PayLocale.ANOTHER_TARGET.locale("en")) {
                AssignMoneyLocale.ANOTHER_TARGET.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = false
                autocomplete = true
            }
        }
    }

    override suspend fun registerCommand() {
        TODO("Not yet implemented")
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        TODO("Not yet implemented")
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        TODO("Not yet implemented")
    }

}