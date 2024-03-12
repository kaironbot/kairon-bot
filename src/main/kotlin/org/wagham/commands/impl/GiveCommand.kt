package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.integer
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotCommand
import org.wagham.commands.SimpleResponseSlashCommand
import org.wagham.components.CacheManager
import org.wagham.components.MultiCharacterCommand
import org.wagham.components.MultiCharacterManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.commands.GiveLocale
import org.wagham.config.locale.components.MultiCharacterLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.TransactionType
import org.wagham.db.models.Character
import org.wagham.db.models.Item
import org.wagham.db.models.embed.Transaction
import org.wagham.entities.InteractionParameters
import org.wagham.utils.*
import java.util.*

@BotCommand("all")
class GiveCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SimpleResponseSlashCommand(), MultiCharacterCommand<GiveCommand.Companion.GiveCommandContext> {

    companion object {
        data class GiveCommandContext(
            val item: Item,
            val amount: Int
        )
    }

    override val commandName = "give"
    override val defaultDescription = GiveLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = GiveLocale.DESCRIPTION.localeMap
    private val multiCharacterManager = MultiCharacterManager(db, kord, this)

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            defaultDescription
        ) {
            localeDescriptions.forEach{ (locale, description) ->
                description(locale, description)
            }
            string("item", GiveLocale.ITEM.locale("en")) {
                GiveLocale.ITEM.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = true
            }
            integer("quantity", GiveLocale.QUANTITY.locale("en")) {
                GiveLocale.QUANTITY.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = true
            }
            user("target", GiveLocale.TARGET.locale("en")) {
                GiveLocale.TARGET.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = true
                autocomplete = true
            }
        }

    }

    override suspend fun multiCharacterAction(
        interaction: ComponentInteraction,
        characters: List<Character>,
        context: GiveCommandContext,
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
                characters.first(),
                context.item,
                context.amount
            )
        }.let { updateBehaviour.edit(it) }
    }

    private suspend fun executeTransaction(
        params: InteractionParameters,
        character: Character,
        targetCharacter: Character,
        item: Item,
        amount: Int
    ) = db.transaction(params.guildId.toString()) { session ->
        val guildId = params.guildId.toString()
        val givenTransaction = Transaction(
            Date(), targetCharacter.id, "GIVE", TransactionType.REMOVE, mapOf(item.name to amount.toFloat())
        )
        val receivedTransaction = Transaction(
            Date(), character.id, "GIVE", TransactionType.ADD, mapOf(item.name to (amount*item.giveRatio))
        )
        db.charactersScope.removeItemFromInventory(session, guildId, character.id, item.name, amount)
        db.charactersScope.addItemToInventory(session, guildId, targetCharacter.id, item.name, (amount*item.giveRatio).toInt())
        db.characterTransactionsScope.addTransactionForCharacter(session, guildId, character.id, givenTransaction)
        db.characterTransactionsScope.addTransactionForCharacter(session, guildId, targetCharacter.id, receivedTransaction)
    }.let {
        if (it.committed) createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(params.locale))
        else createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val params = event.extractCommonParameters()
        val amount = event.interaction.command.integers["quantity"]?.toInt() ?: throw IllegalStateException("Amount not set")
        val item = cacheManager.getCollectionOfType<Item>(params.guildId).firstOrNull {
            event.interaction.command.strings["item"] == it.name
        } ?: throw IllegalStateException("Item not found")
        val target = event.interaction.command.users["target"] ?: throw IllegalStateException("Target not set")
        return withOneActiveCharacterOrErrorMessage(params.responsible, params) { character ->
            when {
                (character.inventory[item.name] ?: 0) < amount ->
                    createGenericEmbedError("${GiveLocale.NOT_ENOUGH_ITEMS.locale(params.locale)} ${item.name}")
                item.giveRatio == 0.0f ->
                    createGenericEmbedError("${GiveLocale.NOT_GIVABLE.locale(params.locale)} ${item.name}")
                else -> {
                    val targetsOrSelectionContext = multiCharacterManager.startSelectionOrReturnCharacters(
                        listOf(target),
                        character,
                        GiveCommandContext(item, amount),
                        params
                    )
                    when {
                        targetsOrSelectionContext.characters != null -> executeTransaction(params, character, targetsOrSelectionContext.characters.first(), item, amount)
                        targetsOrSelectionContext.response != null -> targetsOrSelectionContext.response
                        else -> createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(params.locale))
                    }
                }
            }
        }
    }


}