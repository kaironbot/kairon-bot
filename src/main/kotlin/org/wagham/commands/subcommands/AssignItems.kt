package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.AssignCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.AssignItemLocale
import org.wagham.config.locale.subcommands.AssignItemsLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Item
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.createGenericEmbedError
import org.wagham.utils.createGenericEmbedSuccess
import java.lang.IllegalStateException

@BotSubcommand("all", AssignCommand::class)
class AssignItems(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "items"
    override val defaultDescription = "Assign multiple items to a player"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Assign multiple items to a player",
        Locale.ITALIAN to "Assegna più oggetti a un giocatore"
    )

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        user("target", AssignItemsLocale.TARGET.locale("en")) {
            AssignItemLocale.TARGET.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
            autocomplete = true
        }
        string("items", AssignItemsLocale.ITEMS.locale("en")) {
            AssignItemLocale.ITEM.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
    }

    override suspend fun registerCommand() { }

    private fun parseItemsToAssign(rawItems: String): Map<String, Int> = rawItems.split(";")
        .associate {
            val xIndex = it.lastIndexOf("x")
            val item = it.substring(0, xIndex)
            val qty = it.substring(xIndex+1, it.length).toInt()
            item to qty
        }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val items = cacheManager.getCollectionOfType<Item>(guildId)
        val target = event.interaction.command.users["target"]?.id ?: throw IllegalStateException("Target not found")
        val itemsToAssign = parseItemsToAssign(event.interaction.command.strings["items"] ?: throw IllegalStateException("Target not found"))
        return try {
            val missingItems = itemsToAssign
                .map { it.key }
                .filter { item ->
                    items.firstOrNull { it.name == item } == null
                }
            if(missingItems.isNotEmpty()) {
                createGenericEmbedError(
                    buildString {
                        append(AssignItemsLocale.NOT_FOUND.locale(locale))
                        missingItems.forEach {
                            append(it)
                            append(", ")
                        }
                    }
                )
            } else {
                db.transaction(guildId) { s ->
                    val targetCharacter = db.charactersScope.getActiveCharacter(guildId, target.toString())
                    itemsToAssign.entries.fold(true) { acc, it ->
                        acc && db.charactersScope.addItemToInventory(s, guildId, targetCharacter.id, it.key, it.value)
                    }
                }.let {
                    if (it.committed) createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                    else createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
                }
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
        response.respond(builder)
    }

}