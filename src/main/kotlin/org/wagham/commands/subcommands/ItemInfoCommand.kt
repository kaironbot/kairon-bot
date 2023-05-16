package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.ItemCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.ItemInfoLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.Item
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.levenshteinDistance
import java.lang.IllegalStateException

@BotSubcommand("all", ItemCommand::class)
class ItemInfoCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "info"
    override val defaultDescription = "Show the info about an item"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Show the info about an item",
        Locale.ITALIAN to "Mostra le informazioni riguardanti un oggetto"
    )

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("item", ItemInfoLocale.ITEM.locale("en")) {
            ItemInfoLocale.ITEM.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
    }

    override suspend fun registerCommand() { }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val items = cacheManager.getCollectionOfType<Item>(guildId)
        val rawItem = event.interaction.command.strings["item"] ?: throw IllegalStateException("Item not provided")
        val item = items.firstOrNull { it.name == rawItem }
            ?: items.maxByOrNull { rawItem.levenshteinDistance(it.name) }
            ?: throw IllegalStateException("Item not found")
        return fun InteractionResponseModifyBuilder.() {
            embed {
                title = item.name
                description = item.category
                color = Colors.DEFAULT.value

                field {
                    name = ItemInfoLocale.ATTUNEMENT.locale(locale)
                    value = if (item.attunement) CommonLocale.YES.locale(locale) else CommonLocale.NO.locale(locale)
                    inline = false
                }

                field {
                    name = ItemInfoLocale.BUYABLE.locale(locale)
                    value = if (item.buy != null) CommonLocale.YES.locale(locale) else CommonLocale.NO.locale(locale)
                    inline = false
                }
                if (item.sell != null) {
                    field {
                        name = CommonLocale.PRICE.locale(locale)
                        value = "${item.buy?.cost}"
                        inline = false
                    }
                }

                field {
                    name = ItemInfoLocale.SELLABLE.locale(locale)
                    value = if (item.sell != null) CommonLocale.YES.locale(locale) else CommonLocale.NO.locale(locale)
                    inline = false
                }
                if (item.sell != null) {
                    field {
                        name = CommonLocale.PRICE.locale(locale)
                        value = "${item.sell?.cost}"
                        inline = false
                    }
                }
            }
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