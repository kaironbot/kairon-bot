package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.integer
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotCommand
import org.wagham.commands.SimpleResponseSlashCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.commands.GiveLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Item
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.createGenericEmbedError
import org.wagham.utils.createGenericEmbedSuccess

@BotCommand("all")
class GiveCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SimpleResponseSlashCommand() {

    override val commandName = "give"
    override val defaultDescription = "Give an item to another player"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Give an item to another player",
        Locale.ITALIAN to "Cedi un oggetto a un altro giocatore"
    )

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

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val sender = event.interaction.user.id
        val amount = event.interaction.command.integers["quantity"]?.toInt() ?: throw IllegalStateException("Amount not set")
        val item = cacheManager.getCollectionOfType<Item>(guildId).firstOrNull {
            event.interaction.command.strings["item"] == it.name
        } ?: throw IllegalStateException("Item not found")
        val target = event.interaction.command.users["target"]?.id ?: throw IllegalStateException("Target not set")
        return try {
            val character = db.charactersScope.getActiveCharacter(guildId, sender.toString())
            if((character.inventory[item.name] ?: 0) < amount)
                createGenericEmbedError("${GiveLocale.NOT_ENOUGH_ITEMS.locale(locale)} ${item.name}")
            else {
                db.transaction(guildId) { s ->
                    val targetCharacter = db.charactersScope.getActiveCharacter(guildId, target.toString())
                    db.charactersScope.removeItemFromInventory(s, guildId, character.id, item.name, amount) &&
                     db.charactersScope.addItemToInventory(s, guildId, targetCharacter.id, item.name, (amount*item.giveRatio).toInt())
                }.let {
                    if (it.committed) createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                    else createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
                }
            }

        } catch (e: NoActiveCharacterException) {
            createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
        }
    }


}