package org.wagham.commands.subcommands

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.Locale
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.actionRow
import dev.kord.rest.builder.message.modify.embed
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.ShopCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.AssignItemLocale
import org.wagham.config.locale.subcommands.ShopBuyLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Item
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.createGenericEmbedError
import org.wagham.utils.createGenericEmbedSuccess
import org.wagham.utils.levenshteinDistance
import java.lang.IllegalStateException
import java.util.concurrent.TimeUnit

@BotSubcommand("all", ShopCommand::class)
class ShopBuyCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "buy"
    override val defaultDescription = "Buy an item with the current character"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Buy an item with the current character",
        Locale.ITALIAN to "Compra un oggetto con il personaggio corrente"
    )
    private val interactionCache: Cache<Snowflake, Pair<Snowflake, Snowflake>> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build()

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("item", ShopBuyLocale.ITEM.locale("en")) {
            ShopBuyLocale.ITEM.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
        integer("amount", ShopBuyLocale.AMOUNT.locale("en")) {
            ShopBuyLocale.AMOUNT.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
    }

    override suspend fun registerCommand() {
        kord.on<ButtonInteractionCreateEvent> {
            val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
            if(interaction.componentId.startsWith("${this@AssignItem::class.qualifiedName}") && interactionCache.getIfPresent(interaction.message.id)?.first == interaction.user.id) {
                val guildId = interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
                val targets = interactionCache.getIfPresent(interaction.message.id)?.second ?: throw IllegalStateException("Cannot find targets")
                val (item, amount) = Regex("${this@AssignItem::class.qualifiedName}-(.+)-([0-9]+)")
                    .find(interaction.componentId)
                    ?.groupValues
                    ?.let {
                        Pair(it[1], it[2].toInt())
                    } ?: throw IllegalStateException("Cannot parse parameters")
                assignItemToCharacters(guildId, item, amount, targets).let {
                    when {
                        it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                        it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                        else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
                    }
                }.let {
                    interaction.deferPublicMessageUpdate().edit(it)
                }
            } else if (interaction.componentId.startsWith("${this@AssignItem::class.qualifiedName}") && interactionCache.getIfPresent(interaction.message.id) == null) {
                interaction.deferEphemeralMessageUpdate().edit(createGenericEmbedError(CommonLocale.INTERACTION_EXPIRED.locale(locale)))
            } else if (interaction.componentId.startsWith("${this@AssignItem::class.qualifiedName}")) {
                interaction.deferEphemeralResponse().respond(createGenericEmbedError(CommonLocale.INTERACTION_STARTED_BY_OTHER.locale(locale)))
            }
        }
    }

    private suspend fun assignItemToCharacter(guildId: String, item: Item, amount: Int, player: Snowflake, locale: String) =
        db.transaction(guildId) { s ->
            val character = db.charactersScope.getActiveCharacter(guildId, player.toString())
            db.charactersScope.subtractMoney(s, guildId, character.id, item.buyPrice*amount) &&
                db.charactersScope.addItemToInventory(s, guildId, character.id, item.name, amount)
        }.let {
            when {
                it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
            }
        }

    private suspend fun checkRequirementsAndBuyItem(guildId: String, item: Item, amount: Int, player: Snowflake, locale: String): InteractionResponseModifyBuilder.() -> Unit {
        val character = db.charactersScope.getActiveCharacter(guildId, player.toString())
        return when {
            item.buyPrice <= 0 -> createGenericEmbedError(ShopBuyLocale.CANNOT_BUY.locale(locale))
            character.money < (item.buyPrice * amount) -> createGenericEmbedError(CommonLocale.NOT_ENOUGH_MONEY.locale(locale))
            else -> assignItemToCharacter(guildId, item, amount, player, locale)
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val items = cacheManager.getCollectionOfType<Item>(guildId)
        val amount = event.interaction.command.integers["amount"]?.toInt() ?: throw IllegalStateException("Amount not found")
        val item = event.interaction.command.strings["item"] ?: throw IllegalStateException("Item not found")
        val target = event.interaction.user.id
        return try {
            if (items.firstOrNull { it.name == item } == null) {
                val probableItem = items.maxByOrNull { item.levenshteinDistance(it.name) }
                fun InteractionResponseModifyBuilder.() {
                    embed {
                        title = CommonLocale.ERROR.locale(locale)
                        description = buildString {
                            append(AssignItemLocale.NOT_FOUND.locale(locale))
                            append(item)
                            probableItem?.also {
                                append("\n")
                                append(AssignItemLocale.ALTERNATIVE.locale(locale))
                                append(it.name)
                            }
                        }
                        color = Colors.DEFAULT.value
                    }
                    probableItem?.also {
                        actionRow {
                            interactionButton(ButtonStyle.Primary, "${this@ShopBuyCommand::class.qualifiedName}-${it.name}-$amount") {
                                label = "${AssignItemLocale.ASSIGN_ALTERNATIVE.locale(locale)} ${it.name}"
                            }
                        }
                    }
                }
            } else {
                checkRequirementsAndBuyItem(guildId, items.first { it.name == item }, amount, target, locale)
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