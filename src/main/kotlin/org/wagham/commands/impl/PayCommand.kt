package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.number
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotCommand
import org.wagham.commands.SimpleResponseSlashCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.commands.PayLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.createGenericEmbedError
import org.wagham.utils.createGenericEmbedSuccess
import kotlin.math.floor

@BotCommand("all")
class PayCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SimpleResponseSlashCommand() {

    override val commandName = "pay"
    override val defaultDescription = "Pay another player"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Pay another player",
        Locale.ITALIAN to "Paga un altro giocatore"
    )
    private val additionalUsers: Int = 5

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            defaultDescription
        ) {
            localeDescriptions.forEach{ (locale, description) ->
                description(locale, description)
            }
            number("amount", PayLocale.AMOUNT.locale("en")) {
                PayLocale.AMOUNT.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = true
            }
            user("target", PayLocale.TARGET.locale("en")) {
                PayLocale.TARGET.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = true
                autocomplete = true
            }
            (1 .. additionalUsers).forEach { paramIndex ->
                user("target-$paramIndex", PayLocale.ANOTHER_TARGET.locale("en")) {
                    PayLocale.ANOTHER_TARGET.localeMap.forEach{ (locale, description) ->
                        description(locale, description)
                    }
                    required = false
                    autocomplete = true
                }
            }
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val sender = event.interaction.user.id
        val amount = (event.interaction.command.numbers["amount"]!!.toFloat()).let { floor(it * 100).toInt() / 100f }
        val targets = listOf(
            listOfNotNull(event.interaction.command.users["target"]?.id),
            (1 .. additionalUsers).mapNotNull { paramNum ->
                event.interaction.command.users["target-$paramNum"]?.id
            }
        ).flatten().toSet()
        return try {
            val character = db.charactersScope.getActiveCharacter(guildId, sender.toString())
            if(character.money < (amount * targets.size))
                createGenericEmbedError(PayLocale.NOT_ENOUGH_MONEY.locale(locale))
            else {
                db.transaction(guildId) { s ->
                    val subtraction = db.charactersScope.subtractMoney(s, guildId, character.id, amount*targets.size)
                    targets.fold(subtraction) { acc, it ->
                        val targetCharacter = db.charactersScope.getActiveCharacter(guildId, it.toString())
                        acc && db.charactersScope.addMoney(s, guildId, targetCharacter.id, amount)
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


}