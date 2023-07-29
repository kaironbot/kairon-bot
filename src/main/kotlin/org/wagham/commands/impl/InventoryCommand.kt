package org.wagham.commands.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.Locale
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.User
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.actionRow
import dev.kord.rest.builder.message.modify.embed
import org.wagham.annotations.BotCommand
import org.wagham.commands.SimpleResponseSlashCommand
import org.wagham.components.CacheManager
import org.wagham.components.MultiCharacterCommand
import org.wagham.components.MultiCharacterManager
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.commands.InventoryLocale
import org.wagham.config.locale.commands.ExpLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.Character
import org.wagham.entities.InteractionParameters
import org.wagham.entities.PaginatedList
import org.wagham.utils.*
import java.util.concurrent.TimeUnit

@BotCommand("all")
class InventoryCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SimpleResponseSlashCommand(), MultiCharacterCommand<User> {

    companion object {
        const val previous = "previous"
        const val next  = "next"

        private data class InventoryCacheData(
            val responsible: Snowflake,
            val target: User,
            val money: Float,
            val list: PaginatedList<Pair<String, Int>>,
            val characterName: String
        )
    }

    override val commandName = "inventory"
    override val defaultDescription = InventoryLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = InventoryLocale.DESCRIPTION.localeMap
    private val multiCharacterManager = MultiCharacterManager(db, kord, this)
    private val interactionCache: Cache<String, InventoryCacheData> =
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
            user("target", ExpLocale.TARGET.locale("en")) {
                InventoryLocale.TARGET.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = false
                autocomplete = true
            }
        }
        handleButton()
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val params = event.extractCommonParameters()
        return event.interaction.command.users["target"]?.takeIf { it.id != params.responsible.id }?.let {
            val targetOrSelectionContext = multiCharacterManager.startSelectionOrReturnCharacters(listOf(it), null, it, params)
            when {
                targetOrSelectionContext.characters != null -> generateInventory(targetOrSelectionContext.characters.first(), it, params)
                targetOrSelectionContext.response != null -> targetOrSelectionContext.response
                else -> createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(params.locale))
            }
        } ?: withOneActiveCharacterOrErrorMessage(params.responsible, params) {
            generateInventory(it, params.responsible, params)
        }
    }


    override suspend fun multiCharacterAction(
        interaction: ComponentInteraction,
        characters: List<Character>,
        context: User,
        sourceCharacter: Character?
    ) {
        val params = interaction.extractCommonParameters()
        interaction.deferPublicMessageUpdate().edit(
            generateInventory(characters.first(), context, params)
        )
    }

    private fun generateInventory(character: Character, user: User, params: InteractionParameters): InteractionResponseModifyBuilder.() -> Unit {
        val inventory = PaginatedList(
            character.inventory.toList().sortedBy { it.first },
            pageSize = 15
        )
        val interactionId = compactUuid()
        interactionCache.put(
            interactionId,
            InventoryCacheData(
                params.responsible.id,
                user,
                character.money,
                PaginatedList(character.inventory.toList().sortedBy { it.first }, pageSize = 15),
                character.name
            )
        )
        return generateInventoryEmbed(interactionId, inventory, character.money, user, character.name, params.locale)
    }

    private fun generateInventoryEmbed(interactionId: String, inventory: PaginatedList<Pair<String, Int>>, money: Float, user: User, characterName: String, locale: String): InteractionResponseModifyBuilder.() -> Unit =
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
                interactionButton(ButtonStyle.Secondary, buildElementId(previous, interactionId)) {
                    label = InventoryLocale.LABEL_PREVIOUS.locale(locale)
                }
                interactionButton(ButtonStyle.Secondary, buildElementId(next, interactionId)) {
                    label = InventoryLocale.LABEL_NEXT.locale(locale)
                }
            }
        }

    private fun handleButton() {
        kord.on<ButtonInteractionCreateEvent> {
            if(verifyId(interaction.componentId)) {
                val (op, id) = extractComponentsFromComponentId(interaction.componentId)
                val data = interactionCache.getIfPresent(id)
                val params = interaction.extractCommonParameters()
                when {
                    data == null -> interaction.respondWithExpirationError(params.locale)
                    data.responsible != params.responsible.id -> interaction.respondWithForbiddenError(params.locale)
                    op == previous -> {
                        val newPage = data.list.previousPage()
                        interactionCache.put(id, data.copy(list = newPage))
                        interaction.deferPublicMessageUpdate().edit(
                            generateInventoryEmbed(id, newPage, data.money, data.target, data.characterName, params.locale)
                        )
                    }
                    op == next -> {
                        val newPage = data.list.nextPage()
                        interactionCache.put(id, data.copy(list = newPage))
                        interaction.deferPublicMessageUpdate().edit(
                            generateInventoryEmbed(id, newPage, data.money, data.target, data.characterName, params.locale)
                        )
                    }
                    else -> interaction.respondWithGenericError(params.locale)
                }
            }
        }
    }
}