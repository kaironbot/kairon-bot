package org.wagham.commands.subcommands

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.ItemCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.ItemBuyLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.TransactionType
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Character
import org.wagham.db.models.Item
import org.wagham.db.models.embed.Transaction
import org.wagham.utils.*
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.IllegalStateException

@BotSubcommand("all", ItemCommand::class)
class ItemBuy(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "buy"
    override val defaultDescription = ItemBuyLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = ItemBuyLocale.DESCRIPTION.localeMap
    private val interactionCache: Cache<String, Pair<Snowflake, Character>> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build()

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("item", ItemBuyLocale.ITEM.locale("en")) {
            ItemBuyLocale.ITEM.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
        integer("amount", ItemBuyLocale.AMOUNT.locale("en")) {
            ItemBuyLocale.AMOUNT.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
    }

    override suspend fun registerCommand() {
        kord.on<ButtonInteractionCreateEvent> {
            if(verifyId(interaction.componentId)) {
                val params = interaction.extractCommonParameters()
                val (item, rawAmount, id) = extractComponentsFromComponentId(interaction.componentId)
                val amount = rawAmount.toIntOrNull() ?: throw IllegalStateException("Wrong format for amount")
                val data = interactionCache.getIfPresent(id)
                when {
                    data == null -> interaction.updateWithExpirationError(params.locale)
                    data.first != params.responsible.id -> interaction.respondWithForbiddenError(params.locale)
                    else -> {
                        val items = cacheManager.getCollectionOfType<Item>(params.guildId)
                        checkRequirementsAndBuyItem(params.guildId.toString(), items.first { it.name == item }, amount, data.second, params.locale).let {
                            interaction.deferPublicMessageUpdate().edit(it)
                        }
                    }
                }
            }
        }
    }

    private suspend fun assignItemToCharacter(guildId: String, item: Item, amount: Int, character: String, locale: String) =
        db.transaction(guildId) { s ->
            db.charactersScope.subtractMoney(s, guildId, character, item.buy!!.cost*amount) &&
                db.charactersScope.addItemToInventory(s, guildId, character, item.name, amount) &&
                db.characterTransactionsScope.addTransactionForCharacter(
                    s, guildId, character, Transaction(Date(), null, "BUY", TransactionType.REMOVE, mapOf(transactionMoney to item.buy!!.cost*amount))
                ) &&
                db.characterTransactionsScope.addTransactionForCharacter(
                    s, guildId, character, Transaction(Date(), null, "BUY", TransactionType.ADD, mapOf(item.name to amount.toFloat()))
                )
        }.let {
            when {
                it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
            }
        }

    private suspend fun checkRequirementsAndBuyItem(guildId: String, item: Item, amount: Int, character: Character, locale: String): InteractionResponseModifyBuilder.() -> Unit =
        when {
            item.buy == null -> createGenericEmbedError(ItemBuyLocale.CANNOT_BUY.locale(locale))
            character.money < (item.buy!!.cost * amount) -> createGenericEmbedError(CommonLocale.NOT_ENOUGH_MONEY.locale(locale))
            else -> assignItemToCharacter(guildId, item, amount, character.id, locale)
        }


    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit  = withEventParameters(event) {
        val items = cacheManager.getCollectionOfType<Item>(guildId)
        val amount = event.interaction.command.integers["amount"]?.toInt()?.takeIf { it > 0 }
            ?: throw IllegalStateException("Invalid quantity")
        val item = event.interaction.command.strings["item"] ?: throw IllegalStateException("Item not found")
        withOneActiveCharacterOrErrorMessage(responsible, this) { character ->
            items.firstOrNull { it.name == item }?.let {
                checkRequirementsAndBuyItem(guildId.toString(), items.first { it.name == item }, amount, character, locale)
            } ?: items.maxByOrNull { item.levenshteinDistance(it.name) }?.let { probableItem ->
                val interactionId = compactUuid().substring(0,6)
                interactionCache.put(
                    interactionId,
                    Pair(responsible.id, character)
                )
                alternativeOptionMessage(locale, item, probableItem.name, buildElementId(probableItem.name, amount, interactionId))
            } ?: createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))

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