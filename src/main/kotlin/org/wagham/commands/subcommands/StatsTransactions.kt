package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.StatsCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.subcommands.StatsTransactionLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.utils.guaranteeActiveCharacters
import java.text.SimpleDateFormat

@BotSubcommand("all", StatsCommand::class)
class StatsTransactions(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "transactions"
    override val defaultDescription = "Get a list of the last item and money transactions"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Get a list of the last item and money transactions",
        Locale.ITALIAN to "Visualizza una lista gli ultimi movimenti di oggetti e monete"
    )
    private val lastTransactions = 20
    private val dateFormatter = SimpleDateFormat("dd/MM/yy HH:mm:ss")

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        user("target", StatsTransactionLocale.TARGET.locale("en")) {
            StatsTransactionLocale.TARGET.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = false
            autocomplete = true
        }
    }

    override suspend fun registerCommand() { }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val params = event.extractCommonParameters()
        val target =  event.interaction.command.users["target"] ?: event.interaction.user
        return guaranteeActiveCharacters(params.locale) { locale ->
            val character = db.charactersScope.getActiveCharacter(params.guildId.toString(), target.id.toString())
            val transactions = db.characterTransactionsScope.getLastTransactions(params.guildId.toString(), character.id, lastTransactions)
                .takeIf { it.isNotEmpty() }
            fun InteractionResponseModifyBuilder.() {
                embed {
                    title = "${StatsTransactionLocale.TITLE.locale(locale)} ${character.name}"
                    color = Colors.DEFAULT.value

                    description = transactions?.let { transactions ->
                        buildString {
                            transactions.forEach { t ->
                                append("${dateFormatter.format(t.date)} ")
                                append("${t.operation} ")
                                append("${t.type} ")
                                append(t.args.entries.joinToString(", ") { "${it.key} x${it.value}" })
                                append("\n")
                            }
                        }
                    } ?: StatsTransactionLocale.NO_TRANSACTIONS.locale(locale)
                }
            }
        }
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        response.respond(builder)
    }

}