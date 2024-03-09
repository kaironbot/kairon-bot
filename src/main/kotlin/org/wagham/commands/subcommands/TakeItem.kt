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
import org.wagham.config.locale.subcommands.TakeItemLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.TransactionType
import org.wagham.db.models.Character
import org.wagham.db.models.embed.Transaction
import org.wagham.entities.InteractionParameters
import org.wagham.utils.*
import java.lang.IllegalStateException
import java.util.*

@BotSubcommand("all", TakeCommand::class)
class TakeItem(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder>, MultiCharacterCommand<Pair<String, Int>> {

    override val commandName = "item"
    override val defaultDescription = TakeItemLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = TakeItemLocale.DESCRIPTION.localeMap
    private val multiCharacterManager = MultiCharacterManager(db, kord, this)

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("item", TakeItemLocale.ITEM.locale("en")) {
            TakeItemLocale.ITEM.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
        integer("amount", TakeItemLocale.AMOUNT.locale("en")) {
            TakeItemLocale.AMOUNT.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
        user("target", TakeItemLocale.TARGET.locale("en")) {
            TakeItemLocale.TARGET.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
            autocomplete = true
        }
    }

    override suspend fun registerCommand() { }

    override suspend fun multiCharacterAction(
        interaction: ComponentInteraction,
        characters: List<Character>,
        context: Pair<String, Int>,
        sourceCharacter: Character?
    ) {
        interaction.deferPublicMessageUpdate().edit(
            executeTransaction(characters.first(), context.first, context.second, interaction.extractCommonParameters())
        )
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        val amount = event.interaction.command.integers["amount"]?.toInt() ?: throw IllegalStateException("Amount not found")
        val item = event.interaction.command.strings["item"] ?: throw IllegalStateException("Item not found")
        val target = event.interaction.command.users["target"] ?: throw IllegalStateException("Target not found")
        val targetOrSelectionContext = multiCharacterManager.startSelectionOrReturnCharacters(listOf(target), null, Pair(item, amount), this)
        when {
            targetOrSelectionContext.characters != null -> executeTransaction(targetOrSelectionContext.characters.first(), item, amount, this)
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

    private suspend fun executeTransaction(targetCharacter: Character, item: String, amount: Int, params: InteractionParameters) = with(params) {
        if ((targetCharacter.inventory[item] ?: 0) >= amount) {
            db.transaction(guildId.toString()) { s ->
                db.charactersScope.removeItemFromInventory(s, guildId.toString(), targetCharacter.id, item, amount)
                val result = db.characterTransactionsScope.addTransactionForCharacter(
                    s, guildId.toString(), targetCharacter.id, Transaction(
                        Date(), null, "TAKE", TransactionType.REMOVE, mapOf(item to amount.toFloat())
                    )
                )
                mapOf("result" to result)
            }.let {
                if (it.committed) createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                else createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
            }
        } else createGenericEmbedError("${TakeItemLocale.NOT_FOUND.locale(locale)}$item")
    }
}