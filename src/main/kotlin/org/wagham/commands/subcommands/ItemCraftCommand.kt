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
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.integer
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.commands.SubCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.ItemBuyLocale
import org.wagham.config.locale.subcommands.ItemCraftLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Item
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.alternativeOptionMessage
import org.wagham.utils.createGenericEmbedError
import org.wagham.utils.createGenericEmbedSuccess
import org.wagham.utils.levenshteinDistance
import java.lang.IllegalStateException
import java.util.concurrent.TimeUnit

class ItemCraftCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "craft"
    override val defaultDescription = "Craft an item with the current character"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Craft an item with the current character",
        Locale.ITALIAN to "Costruisci un oggetto con il personaggio corrente"
    )
    private val interactionCache: Cache<Snowflake, Snowflake> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build()

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("item", ItemCraftLocale.ITEM.locale("en")) {
            ItemCraftLocale.ITEM.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
        integer("amount", ItemCraftLocale.AMOUNT.locale("en")) {
            ItemCraftLocale.AMOUNT.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
    }

    override suspend fun registerCommand() {
        kord.on<ButtonInteractionCreateEvent> {
            val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
            if(interaction.componentId.startsWith("${this@ItemCraftCommand::class.qualifiedName}") && interactionCache.getIfPresent(interaction.message.id) == interaction.user.id) {
                val guildId = interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
                val target = interactionCache.getIfPresent(interaction.message.id) ?: throw IllegalStateException("Cannot find targets")
                val (item, amount) = Regex("${this@ItemCraftCommand::class.qualifiedName}-(.+)-([0-9]+)")
                    .find(interaction.componentId)
                    ?.groupValues
                    ?.let {
                        Pair(it[1], it[2].toInt())
                    } ?: throw IllegalStateException("Cannot parse parameters")
                val items = cacheManager.getCollectionOfType<Item>(guildId)
                checkRequirementsAndCraftItem(guildId, items.first { it.name == item }, amount, target, locale).let {
                    interaction.deferPublicMessageUpdate().edit(it)
                }
            } else if (interaction.componentId.startsWith("${this@ItemCraftCommand::class.qualifiedName}") && interactionCache.getIfPresent(interaction.message.id) == null) {
                interaction.deferEphemeralMessageUpdate().edit(createGenericEmbedError(CommonLocale.INTERACTION_EXPIRED.locale(locale)))
            } else if (interaction.componentId.startsWith("${this@ItemCraftCommand::class.qualifiedName}")) {
                interaction.deferEphemeralResponse().respond(createGenericEmbedError(CommonLocale.INTERACTION_STARTED_BY_OTHER.locale(locale)))
            }
        }
    }

    private suspend fun assignItemToCharacter(guildId: String, item: Item, amount: Int, character: String, locale: String) =
        db.transaction(guildId) { s ->
            db.charactersScope.subtractMoney(s, guildId, character, item.buyPrice*amount) &&
                    db.charactersScope.addItemToInventory(s, guildId, character, item.name, amount)
        }.let {
            when {
                it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
            }
        }

    private suspend fun checkRequirementsAndCraftItem(guildId: String, item: Item, amount: Int, player: Snowflake, locale: String): InteractionResponseModifyBuilder.() -> Unit {
        val character = db.charactersScope.getActiveCharacter(guildId, player.toString())
        return when {
            item.craft != null -> createGenericEmbedError(ItemBuyLocale.CANNOT_BUY.locale(locale))
            character.money < (item.buyPrice * amount) -> createGenericEmbedError(CommonLocale.NOT_ENOUGH_MONEY.locale(locale))
            else -> assignItemToCharacter(guildId, item, amount, character.id, locale)
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val items = cacheManager.getCollectionOfType<Item>(guildId)
        val amount = event.interaction.command.integers["amount"]?.toInt()?.takeIf { it > 0 }
            ?: throw IllegalStateException(ItemCraftLocale.INVALID_QTY.locale(locale))
        val item = event.interaction.command.strings["item"] ?: throw IllegalStateException("Item not provided")
        val target = event.interaction.user.id
        return try {
            if (items.firstOrNull { it.name == item } == null) {
                val probableItem = items.maxByOrNull { item.levenshteinDistance(it.name) }
                alternativeOptionMessage(locale, item, probableItem?.name, "${this@ItemCraftCommand::class.qualifiedName}-${probableItem?.name}-$amount")
            } else {
                checkRequirementsAndCraftItem(guildId, items.first { it.name == item }, amount, target, locale)
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
        val msg = response.respond(builder)
        interactionCache.put(
            msg.message.id,
            event.interaction.user.id
        )
    }

}