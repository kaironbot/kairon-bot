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
import org.wagham.config.locale.subcommands.TakeMoneyLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.createGenericEmbedError
import org.wagham.utils.createGenericEmbedSuccess
import kotlin.math.floor

@BotSubcommand("all", TakeCommand::class)
class TakeMoney(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "money"
    override val defaultDescription = "Take an amount of money from a player"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Take an amount of money from a player",
        Locale.ITALIAN to "Rimuovi delle monete a un giocatore"
    )
    private val additionalUsers: Int = 5

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        number("amount", TakeMoneyLocale.AMOUNT.locale("en")) {
            TakeMoneyLocale.AMOUNT.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
        user("target", TakeMoneyLocale.TARGET.locale("en")) {
            TakeMoneyLocale.TARGET.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
            autocomplete = true
        }
        (1 .. additionalUsers).forEach { paramIndex ->
            user("target-$paramIndex", TakeMoneyLocale.ANOTHER_TARGET.locale("en")) {
                TakeMoneyLocale.ANOTHER_TARGET.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = false
                autocomplete = true
            }
        }
    }

    override suspend fun registerCommand() { }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val targets = listOf(
            listOfNotNull(event.interaction.command.users["target"]?.id),
            (1 .. additionalUsers).mapNotNull { paramNum ->
                event.interaction.command.users["target-$paramNum"]?.id
            }
        ).flatten().toSet()
        val amount = (event.interaction.command.numbers["amount"]!!.toFloat()).let { floor(it * 100).toInt() / 100f }
        return try {
            db.transaction(guildId) { s ->
                targets.fold(true) { acc, it ->
                    val targetCharacter = db.charactersScope.getActiveCharacter(guildId, it.toString())
                    acc && db.charactersScope.subtractMoney(s, guildId, targetCharacter.id, amount)
                }
            }.let {
                when {
                    it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                    it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                    else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
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