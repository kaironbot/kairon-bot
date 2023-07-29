package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.integer
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.ItemCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.ItemSellLocale
import org.wagham.config.locale.subcommands.ItemUseLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.TransactionType
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Character
import org.wagham.db.models.Item
import org.wagham.db.models.embed.Transaction
import org.wagham.utils.*
import java.lang.IllegalStateException
import java.util.*

@BotSubcommand("all", ItemCommand::class)
class ItemUse(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "use"
    override val defaultDescription = ItemUseLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = ItemUseLocale.DESCRIPTION.localeMap

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("item", ItemUseLocale.ITEM.locale("en")) {
            ItemUseLocale.ITEM.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
        integer("amount", ItemUseLocale.AMOUNT.locale("en")) {
            ItemUseLocale.AMOUNT.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
    }

    override suspend fun registerCommand() { }

    private suspend fun removeItemFromCharacter(guildId: String, item: Item, amount: Int, character: String, locale: String) =
        db.transaction(guildId) { s ->
            db.charactersScope.removeItemFromInventory(s, guildId, character, item.name, amount) &&
            db.characterTransactionsScope.addTransactionForCharacter(
                s, guildId, character, Transaction(Date(), null, "USE", TransactionType.REMOVE, mapOf(item.name to amount.toFloat()))
            )
        }.let {
            when {
                it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
            }
        }

    private suspend fun checkRequirementsAndUseItem(guildId: String, item: Item, amount: Int, character: Character, locale: String): InteractionResponseModifyBuilder.() -> Unit =
        when {
            !item.usable -> createGenericEmbedError(ItemUseLocale.CANNOT_USE.locale(locale))
            (character.inventory[item.name] ?: 0) < amount -> createGenericEmbedError("${CommonLocale.NOT_ENOUGH_ITEMS.locale(locale)}${item.name}")
            else -> removeItemFromCharacter(guildId, item, amount, character.id, locale)
        }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        val amount = event.interaction.command.integers["amount"]?.toInt()?.takeIf { it > 0 }
            ?: throw IllegalStateException("Invalid quantity")
        val item = event.interaction.command.strings["item"]?.let { query ->
            cacheManager.getCollectionOfType<Item>(guildId).firstOrNull { it.name == query }
        } ?: throw IllegalStateException(ItemSellLocale.NOT_FOUND.locale(locale))
        withOneActiveCharacterOrErrorMessage(responsible, this) {
            checkRequirementsAndUseItem(guildId.toString(), item, amount, it, locale)
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