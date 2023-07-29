package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.integer
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotCommand
import org.wagham.commands.SimpleResponseSlashCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.commands.ConvertTBadgeLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.TransactionType
import org.wagham.db.models.embed.Transaction
import org.wagham.utils.*
import java.util.*

@BotCommand("wagham")
class ConvertTBadgeCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SimpleResponseSlashCommand() {

    override val commandName = "convert_tbadge"
    override val defaultDescription = ConvertTBadgeLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = ConvertTBadgeLocale.DESCRIPTION.localeMap

    enum class TBadge(val itemName: String, val rank: Int) {
        T1_BADGE("1DayT1Badge", 1),
        T2_BADGE("1DayT2Badge", 2),
        T3_BADGE("1DayT3Badge", 4),
        T4_BADGE("1DayT4Badge", 8),
        T5_BADGE("1DayT5Badge", 16)
    }

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            defaultDescription
        ) {
            localeDescriptions.forEach{ (locale, description) ->
                description(locale, description)
            }
            string("source_type", ConvertTBadgeLocale.SOURCE_TYPE.locale("en")) {
                ConvertTBadgeLocale.SOURCE_TYPE.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                TBadge.values().forEach {
                    choice(it.itemName, it.name)
                }
                required = true
            }
            string("destination_type", ConvertTBadgeLocale.DESTINATION_TYPE.locale("en")) {
                ConvertTBadgeLocale.DESTINATION_TYPE.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                TBadge.values().forEach {
                    choice(it.itemName, it.name)
                }
                required = true
            }
            integer("amount", ConvertTBadgeLocale.AMOUNT.locale("en")) {
                ConvertTBadgeLocale.AMOUNT.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = true
            }
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val params = event.extractCommonParameters()
        val source = event.interaction.command.strings["source_type"]?.let {
            TBadge.valueOf(it)
        } ?: throw IllegalStateException("Source type not found")
        val target = event.interaction.command.strings["destination_type"]?.let {
            TBadge.valueOf(it)
        } ?: throw IllegalStateException("Destination type not found")
        val amount = event.interaction.command.integers["amount"]?.toInt() ?: throw IllegalStateException("Amount not found")
        val destinationAmount = amount.let {
            if(source.rank >= target.rank) it
            else (it*source.rank/target.rank)
        }
        return withOneActiveCharacterOrErrorMessage(params.responsible, params) { character ->
            if (character.inventory.getOrDefault(source.itemName, 0) < amount) {
                createGenericEmbedError("${ConvertTBadgeLocale.NOT_ENOUGH_TBADGE.locale(params.locale)} ${source.itemName}")
            } else {
                db.transaction(params.guildId.toString()) { session ->
                    val takeStep = db.charactersScope.removeItemFromInventory(session, params.guildId.toString(), character.id, source.itemName, amount)
                    val giveStep = db.charactersScope.addItemToInventory(session, params.guildId.toString(), character.id, target.itemName, destinationAmount)
                    val reportStep = db.characterTransactionsScope.addTransactionForCharacter(
                        session, params.guildId.toString(), character.id, Transaction(
                            Date(), null, "CONVERT_TBADGE", TransactionType.REMOVE, mapOf(source.itemName to amount.toFloat()))
                    ) && db.characterTransactionsScope.addTransactionForCharacter(
                        session, params.guildId.toString(), character.id, Transaction(Date(), null, "CONVERT_TBADGE", TransactionType.ADD, mapOf(target.itemName to destinationAmount.toFloat()))
                    )
                    takeStep && giveStep && reportStep
                }.let {
                    if (it.committed) createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(params.locale))
                    else createGenericEmbedError(CommonLocale.ERROR.locale(params.locale))
                }
            }
        }

    }

}