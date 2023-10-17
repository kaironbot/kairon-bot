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
import org.wagham.config.locale.subcommands.ItemCraftLocale
import org.wagham.config.locale.subcommands.ItemInfoLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.Item
import org.wagham.utils.defaultLocale
import org.wagham.utils.levenshteinDistance
import org.wagham.utils.summary
import org.wagham.utils.withEventParameters
import java.lang.IllegalStateException

@BotSubcommand("all", ItemCommand::class)
class ItemInfo(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "info"
    override val defaultDescription = ItemInfoLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = ItemInfoLocale.DESCRIPTION.localeMap

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

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
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
                url = item.link

                field {
                    name = ItemInfoLocale.ATTUNEMENT.locale(locale)
                    value = if (item.attunement) CommonLocale.YES.locale(locale) else CommonLocale.NO.locale(locale)
                    inline = false
                }

                field {
                    name = ItemInfoLocale.CAN_BUY.locale(locale)
                    value = if (item.buy != null) "${item.buy?.cost} MO" else ItemInfoLocale.CANNOT_BUY.locale(locale)
                    inline = true
                }

                field {
                    name = ItemInfoLocale.SELLABLE.locale(locale)
                    value = if (item.sell != null) "${item.sell?.cost} MO" else ItemInfoLocale.CANNOT_SELL.locale(locale)
                    inline = true
                }

                field {
                    name = ItemInfoLocale.CAN_GIVE.locale(locale)
                    value = if (item.giveRatio > 0) "${(1/item.giveRatio).toInt()}:1" else ItemInfoLocale.CANNOT_GIVE.locale(locale)
                    inline = true
                }

                field {
                    name = ItemInfoLocale.CAN_USE.locale(locale)
                    value = if (item.usable) CommonLocale.YES.locale(locale) else CommonLocale.NO.locale(locale)
                    inline = true
                }
                field {
                    name = "Crafting"
                    value = if (item.craft.isNotEmpty()) ItemInfoLocale.CAN_CRAFT.locale(locale) else ItemInfoLocale.CANNOT_CRAFT.locale(locale)
                    inline = false
                }
                item.craft.forEachIndexed { idx, recipe ->
                    field {
                        name = recipe.label ?: "${ItemCraftLocale.RECIPE.locale(locale)} $idx"
                        value = recipe.summary()
                        inline = false
                    }
                }
                if(item.manual != null) {
                    field {
                        name = ItemInfoLocale.SOURCE.locale(locale)
                        value = item.manual!!
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