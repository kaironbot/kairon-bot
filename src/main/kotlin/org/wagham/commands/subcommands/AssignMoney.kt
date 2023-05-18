package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
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
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.commands.PayLocale
import org.wagham.config.locale.subcommands.AssignMoneyLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.TransactionType
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.embed.Transaction
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.createGenericEmbedError
import org.wagham.utils.createGenericEmbedSuccess
import org.wagham.utils.transactionMoney
import java.util.*
import kotlin.math.floor

@BotSubcommand("all", AssignCommand::class)
class AssignMoney(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "money"
    override val defaultDescription = "Assign money to one or more players"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Assign money to one or more players",
        Locale.ITALIAN to "Assegna delle monete a uno o più giocatori"
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
            required = true
            autocomplete = true
        }
        (1 .. additionalUsers).forEach { paramIndex ->
            user("target-$paramIndex", AssignMoneyLocale.ANOTHER_TARGET.locale("en")) {
                AssignMoneyLocale.ANOTHER_TARGET.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = false
                autocomplete = true
            }
        }
    }

    override suspend fun registerCommand() { }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val targets = listOf(
            listOfNotNull(event.interaction.command.users["target"]?.id),
            (1 .. additionalUsers).mapNotNull { paramNum ->
                event.interaction.command.users["target-$paramNum"]?.id
            }
        ).flatten().toSet()
        val amount = (event.interaction.command.numbers["amount"]!!.toFloat()).let { floor(it * 100).toInt() / 100f }
        return try {
            db.transaction(guildId) { s ->
                    targets.fold(true) { acc, it ->
                        val targetCharacter = db.charactersScope.getActiveCharacter(guildId, it.toString())
                        acc && db.charactersScope.addMoney(s, guildId, targetCharacter.id, amount) &&
                            db.characterTransactionsScope.addTransactionForCharacter(
                                s, guildId, targetCharacter.id, Transaction(
                                    Date(), null, "ASSIGN", TransactionType.ADD, mapOf(transactionMoney to amount)
                                )
                            )
                    }
                }.let {
                    when {
                        it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                        it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                        else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
                    }
                }
        } catch (e: NoActiveCharacterException) {
            createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
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