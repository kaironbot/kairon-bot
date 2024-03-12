package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.number
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotCommand
import org.wagham.commands.SimpleResponseSlashCommand
import org.wagham.components.CacheManager
import org.wagham.components.MultiCharacterCommand
import org.wagham.components.MultiCharacterManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.commands.PayLocale
import org.wagham.config.locale.components.MultiCharacterLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.TransactionType
import org.wagham.db.models.Character
import org.wagham.db.models.embed.Transaction
import org.wagham.entities.InteractionParameters
import org.wagham.utils.*
import java.util.*
import kotlin.math.floor

@BotCommand("all")
class PayCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SimpleResponseSlashCommand(), MultiCharacterCommand<Float> {

    override val commandName = "pay"
    override val defaultDescription = PayLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = PayLocale.DESCRIPTION.localeMap
    private val additionalUsers: Int = 5
    private val multiCharacterManager = MultiCharacterManager(db, kord, this)

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            defaultDescription
        ) {
            localeDescriptions.forEach{ (locale, description) ->
                description(locale, description)
            }
            number("amount", PayLocale.AMOUNT.locale("en")) {
                PayLocale.AMOUNT.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = true
            }
            user("target", PayLocale.TARGET.locale("en")) {
                PayLocale.TARGET.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = true
                autocomplete = true
            }
            (1 .. additionalUsers).forEach { paramIndex ->
                user("target-$paramIndex", PayLocale.ANOTHER_TARGET.locale("en")) {
                    PayLocale.ANOTHER_TARGET.localeMap.forEach{ (locale, description) ->
                        description(locale, description)
                    }
                    required = false
                    autocomplete = true
                }
            }
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val params = event.extractCommonParameters()
        val amount = (event.interaction.command.numbers["amount"]?.toFloat())?.let {
            floor(it * 100).toInt() / 100f
        } ?: throw IllegalStateException("Amount not found")
        val targets = listOf(
            listOfNotNull(event.interaction.command.users["target"]),
            (1 .. additionalUsers).mapNotNull { paramNum ->
                event.interaction.command.users["target-$paramNum"]
            }
        ).flatten().toSet()
        return withOneActiveCharacterOrErrorMessage(params.responsible, params) { character ->
            if(character.money < (amount * targets.size))
                createGenericEmbedError(PayLocale.NOT_ENOUGH_MONEY.locale(params.locale))
            else {
                val targetsOrSelectionContext = multiCharacterManager.startSelectionOrReturnCharacters(
                    targets.toList(),
                    character,
                    amount,
                    params
                )
                when {
                    targetsOrSelectionContext.characters != null ->  executeTransaction(params, character, targetsOrSelectionContext.characters, amount)
                    targetsOrSelectionContext.response != null -> targetsOrSelectionContext.response
                    else -> createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(params.locale))
                }
            }
        }
    }

    override suspend fun multiCharacterAction(
        interaction: ComponentInteraction,
        characters: List<Character>,
        context: Float,
        sourceCharacter: Character?
    ) {
        val params = interaction.extractCommonParameters()
        val updateBehaviour = interaction.deferPublicMessageUpdate()
        if(sourceCharacter == null) {
            createGenericEmbedError(MultiCharacterLocale.NO_SOURCE.locale(params.locale))
        } else {
            executeTransaction(
                params,
                sourceCharacter,
                characters,
                context
            )
        }.let { updateBehaviour.edit(it) }
    }

    private suspend fun executeTransaction(params: InteractionParameters, sourceCharacter: Character, targetCharacters: Collection<Character>, amount: Float) =
        db.transaction(params.guildId.toString()) { session ->
            db.charactersScope.subtractMoney(session, params.guildId.toString(), sourceCharacter.id, amount*targetCharacters.size)
            targetCharacters.forEach { targetCharacter ->
                val givenTransaction = Transaction(
                    Date(), targetCharacter.id, "PAY", TransactionType.REMOVE, mapOf(transactionMoney to amount)
                )
                val receivedTransaction = Transaction(
                    Date(), sourceCharacter.id, "PAY", TransactionType.ADD, mapOf(transactionMoney to amount)
                )
                db.charactersScope.addMoney(session, params.guildId.toString(), targetCharacter.id, amount)
                db.characterTransactionsScope.addTransactionForCharacter(session, params.guildId.toString(), sourceCharacter.id, givenTransaction)
                db.characterTransactionsScope.addTransactionForCharacter(session, params.guildId.toString(), targetCharacter.id, receivedTransaction)
            }
        }.let {
            if (it.committed) createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(params.locale))
            else createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
        }
}