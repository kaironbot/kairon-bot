package org.wagham.commands.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.Locale
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.User
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.actionRow
import dev.kord.rest.builder.message.modify.embed
import org.wagham.annotations.BotCommand
import org.wagham.commands.SlashCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.commands.InventoryLocale
import org.wagham.config.locale.commands.MSLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.entities.PaginatedList
import org.wagham.utils.createGenericEmbedError
import java.util.concurrent.TimeUnit

@BotCommand("all")
class InventoryCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SlashCommand<InteractionResponseModifyBuilder>() {

    override val commandName = "inventory"
    override val defaultDescription = "Show your inventory or the inventory of another player"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Show your inventory or the inventory of another player",
        Locale.ITALIAN to "Mostra il tuo inventario o l'inventario di un altro giocatore"
    )
    private val interactionCache: Cache<Snowflake, InventoryCacheData> =
        Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build()

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            defaultDescription
        ) {
            localeDescriptions.forEach{ (locale, description) ->
                description(locale, description)
            }
            user("target", MSLocale.TARGET.locale("en")) {
                InventoryLocale.TARGET.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = false
                autocomplete = true
            }
        }
        kord.on<ButtonInteractionCreateEvent> {
            val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
            if(interaction.componentId.startsWith("${this@InventoryCommand::class.qualifiedName}") && interactionCache.getIfPresent(interaction.message.id)?.user == interaction.user.id) {
                when(interaction.componentId) {
                    "${this@InventoryCommand::class.qualifiedName}-previous" -> {
                        val currentData = interactionCache.getIfPresent(interaction.message.id)!!
                        val newPage = currentData.list.previousPage()
                        interactionCache.put(interaction.message.id, currentData.copy(list = newPage))
                        interaction.deferPublicMessageUpdate().edit(generateInventoryEmbed(newPage, currentData.money, currentData.target, currentData.characterName, locale))
                    }
                    "${this@InventoryCommand::class.qualifiedName}-next" -> {
                        val currentData = interactionCache.getIfPresent(interaction.message.id)!!
                        val newPage = currentData.list.nextPage()
                        interactionCache.put(interaction.message.id,  currentData.copy(list = newPage))
                        interaction.deferPublicMessageUpdate().edit(generateInventoryEmbed(newPage, currentData.money, currentData.target, currentData.characterName, locale))
                    }
                    else -> {
                        interaction.deferPublicMessageUpdate().edit(createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale)))
                    }
                }
            } else if (interaction.componentId.startsWith("${this@InventoryCommand::class.qualifiedName}") && interactionCache.getIfPresent(interaction.message.id) == null) {
                interaction.deferEphemeralMessageUpdate().edit {
                    embed {
                        title = CommonLocale.INTERACTION_EXPIRED.locale(locale)
                        color = Colors.DEFAULT.value
                    }
                    components = mutableListOf()
                }
            } else if (interaction.componentId.startsWith("${this@InventoryCommand::class.qualifiedName}")) {
                interaction.deferEphemeralResponse().respond(createGenericEmbedError(CommonLocale.INTERACTION_STARTED_BY_OTHER.locale(locale)))
            }
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val guildId = event.interaction.guildId
        val user = event.interaction.command.users["target"] ?: event.interaction.user
        return try {
            val character =  db.charactersScope.getActiveCharacter(guildId.toString(), user.id.toString())
            val inventory = PaginatedList(
                character.inventory.toList().sortedBy { it.first },
                pageSize = 15
            )
            generateInventoryEmbed(inventory, character.money, user, character.name, locale)
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
        val guildId = event.interaction.guildId
        val user = event.interaction.command.users["target"] ?: event.interaction.user
        val character = db.charactersScope.getActiveCharacter(guildId.toString(), user.id.toString())
        interactionCache.put(
            msg.message.id,
            InventoryCacheData(
                event.interaction.user.id,
                user,
                character.money,
                PaginatedList(character.inventory.toList().sortedBy { it.first }, pageSize = 15),
                character.name
            )
        )
    }

    private fun generateInventoryEmbed(inventory: PaginatedList<Pair<String, Int>>, money: Float, user: User, characterName: String, locale: String): InteractionResponseModifyBuilder.() -> Unit =
        fun InteractionResponseModifyBuilder.() {
            embed {
                author {
                    name = characterName
                    icon = user.avatar?.url
                }
                description = buildString {
                    append("**${InventoryLocale.MONEY.locale(locale)}**\n")
                    append("$money MO\n\n")
                    append("**${InventoryLocale.ITEMS.locale(locale)}**\n")
                    inventory.page.forEach {
                        append("${it.first} x${it.second}\n")
                    }
                }
                color = Colors.DEFAULT.value
            }
            actionRow {
                interactionButton(ButtonStyle.Secondary, "${this@InventoryCommand::class.qualifiedName}-previous") {
                    label = InventoryLocale.LABEL_PREVIOUS.locale(locale)
                }
                interactionButton(ButtonStyle.Secondary, "${this@InventoryCommand::class.qualifiedName}-next") {
                    label = InventoryLocale.LABEL_NEXT.locale(locale)
                }
            }
        }

    private data class InventoryCacheData(
        val user: Snowflake,
        val target: User,
        val money: Float,
        val list: PaginatedList<Pair<String, Int>>,
        val characterName: String
    )
}