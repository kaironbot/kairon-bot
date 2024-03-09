package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.TakeCommand
import org.wagham.components.CacheManager
import org.wagham.components.MultiCharacterCommand
import org.wagham.components.MultiCharacterManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.TakeMoneyLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.TransactionType
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Character
import org.wagham.db.models.embed.Transaction
import org.wagham.entities.InteractionParameters
import org.wagham.utils.*
import java.util.*
import kotlin.math.floor

@BotSubcommand("all", TakeCommand::class)
class TakeMoney(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder>, MultiCharacterCommand<Float> {

    override val commandName = "money"
    override val defaultDescription = TakeMoneyLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = TakeMoneyLocale.DESCRIPTION.localeMap
    private val multiCharacterManager = MultiCharacterManager(db, kord, this)
    private val additionalUsers: Int = 5

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        number("amount", TakeMoneyLocale.AMOUNT.locale("en")) {
            TakeMoneyLocale.AMOUNT.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
        user("target", TakeMoneyLocale.TARGET.locale("en")) {
            TakeMoneyLocale.TARGET.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
            autocomplete = true
        }
        (1 .. additionalUsers).forEach { paramIndex ->
            user("target-$paramIndex", TakeMoneyLocale.ANOTHER_TARGET.locale("en")) {
                TakeMoneyLocale.ANOTHER_TARGET.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = false
                autocomplete = true
            }
        }
    }

    override suspend fun registerCommand() { }


    override suspend fun multiCharacterAction(
        interaction: ComponentInteraction,
        characters: List<Character>,
        context: Float,
        sourceCharacter: Character?
    ) {
        interaction.deferPublicMessageUpdate().edit(
            executeTransaction(characters, context, interaction.extractCommonParameters())
        )
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        val targets = listOf(
            listOfNotNull(event.interaction.command.users["target"]),
            (1 .. additionalUsers).mapNotNull { paramNum ->
                event.interaction.command.users["target-$paramNum"]
            }
        ).flatten().toSet().toList()
        val amount = event.interaction.command.numbers["amount"]?.toFloat()?.let { floor(it * 100).toInt() / 100f }
            ?: throw IllegalStateException("Invalid amount")
        val targetOrSelectionContext = multiCharacterManager.startSelectionOrReturnCharacters(targets, null, amount, this)
        when {
            targetOrSelectionContext.characters != null -> executeTransaction(targetOrSelectionContext.characters, amount, this)
            targetOrSelectionContext.response != null -> targetOrSelectionContext.response
            else -> createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))
        }
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        response.respond(builder)
    }

    private suspend fun executeTransaction(targets: Collection<Character>, amount: Float, params: InteractionParameters) = with(params) {
        db.transaction(guildId.toString()) { s ->
            val result = targets.fold(true) { acc, targetCharacter ->
                acc && db.charactersScope.subtractMoney(s, guildId.toString(), targetCharacter.id, amount)&&
                        db.characterTransactionsScope.addTransactionForCharacter(
                            s, guildId.toString(), targetCharacter.id, Transaction(
                                Date(), null, "TAKE", TransactionType.REMOVE, mapOf(transactionMoney to amount)
                            )
                        )
            }
            mapOf("result" to result)
        }.let {
            when {
                it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
            }
        }
    }
}