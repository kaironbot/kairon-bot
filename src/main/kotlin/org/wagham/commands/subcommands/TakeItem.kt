package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.TakeCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.TakeItemLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.createGenericEmbedError
import org.wagham.utils.createGenericEmbedSuccess
import java.lang.IllegalStateException

@BotSubcommand("all", TakeCommand::class)
class TakeItem(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "item"
    override val defaultDescription = "Take an item from a player"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Assign an item to one or more players",
        Locale.ITALIAN to "Togli un ogetto a un giocatore"
    )

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

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val amount = event.interaction.command.integers["amount"]?.toInt() ?: throw IllegalStateException("Amount not found")
        val item = event.interaction.command.strings["item"] ?: throw IllegalStateException("Item not found")
        val target = event.interaction.command.users["target"]?.id ?: throw IllegalStateException("Target not found")
        return try {
            val targetCharacter = db.charactersScope.getActiveCharacter(guildId, target.toString())
            if ( (targetCharacter.inventory[item] ?: 0) >= amount) {
                db.transaction(guildId) { s ->
                    db.charactersScope.removeItemFromInventory(s, guildId, targetCharacter.id, item, amount)
                }.let {
                    if (it.committed) createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                    else createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
                }
            } else createGenericEmbedError("${TakeItemLocale.NOT_FOUND.locale(locale)}$item")

        } catch (e: NoActiveCharacterException) {
            createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
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