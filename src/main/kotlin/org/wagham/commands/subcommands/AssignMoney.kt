package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ComponentInteraction
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
import org.wagham.components.MultiCharacterCommand
import org.wagham.components.MultiCharacterManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.AssignMoneyLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.TransactionType
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Character
import org.wagham.db.models.embed.Transaction
import org.wagham.entities.InteractionParameters
import org.wagham.utils.*
import java.util.*
import kotlin.math.floor

@BotSubcommand("all", AssignCommand::class)
class AssignMoney(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder>, MultiCharacterCommand<Float> {

    override val commandName = "money"
    override val defaultDescription = AssignMoneyLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = AssignMoneyLocale.DESCRIPTION.localeMap
    private val additionalUsers: Int = 5
    private val multiCharacterManager = MultiCharacterManager(db, kord, this)

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

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        val targets = listOf(
            listOfNotNull(event.interaction.command.users["target"]),
            (1 .. additionalUsers).mapNotNull { paramNum ->
                event.interaction.command.users["target-$paramNum"]
            }
        ).flatten().toSet()
        val amount = event.interaction.command.numbers["amount"]?.toFloat()?.let { floor(it * 100).toInt() / 100f }
            ?: throw IllegalStateException("Amount not found")
        val targetsOrSelectionContext = multiCharacterManager.startSelectionOrReturnCharacters(targets.toList(), null, amount, this)
        when {
            targetsOrSelectionContext.characters != null -> assignMoney(amount, targetsOrSelectionContext.characters, this)
            targetsOrSelectionContext.response != null -> targetsOrSelectionContext.response
            else -> createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))
        }
    }

    override suspend fun multiCharacterAction(
        interaction: ComponentInteraction,
        characters: List<Character>,
        context: Float,
        sourceCharacter: Character?
    ) {
        assignMoney(context, characters, interaction.extractCommonParameters()).let {
            interaction.deferPublicMessageUpdate().edit(it)
        }
    }

    private suspend fun assignMoney(amount: Float, targets: Collection<Character>, params: InteractionParameters) =
        db.transaction(params.guildId.toString()) { s ->
            targets.fold(true) { acc, targetCharacter ->
                acc && db.charactersScope.addMoney(s, params.guildId.toString(), targetCharacter.id, amount) &&
                        db.characterTransactionsScope.addTransactionForCharacter(
                            s, params.guildId.toString(), targetCharacter.id, Transaction(
                                Date(), null, "ASSIGN", TransactionType.ADD, mapOf(transactionMoney to amount)
                            )
                        )
            }
        }.let {
            when {
                it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(params.locale))
                it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(params.locale))
                else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
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