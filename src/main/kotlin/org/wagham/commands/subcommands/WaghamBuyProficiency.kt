package org.wagham.commands.subcommands

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.Locale
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.actionRow
import dev.kord.rest.builder.message.modify.embed
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.impl.BuyCommand
import org.wagham.commands.SubCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.BuyProficiencyLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.createGenericEmbedError
import org.wagham.utils.createGenericEmbedSuccess
import org.wagham.utils.levenshteinDistance
import java.util.concurrent.TimeUnit

@BotSubcommand("wagham", BuyCommand::class)
class WaghamBuyProficiency(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "proficiency"
    override val defaultDescription = "Search for a proficiency and buy it"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Search for a proficiency and buy it",
        Locale.ITALIAN to "Cerca una competenza e acquistala"
    )

    private val giveMoneyBadge = listOf(
        "Cartographer's tools",
        "Cobbler's tools",
        "Forgery kit",
        "Gaming Set - Dice Set",
        "Gaming Set - Dragonchess Set",
        "Gaming Set - Playing Card Set",
        "Gaming Set - Three-Dragon Ante Set",
        "Glassblower's tools",
        "Navigator's tools",
        "Poisoner's kit",
        "Potter's tools",
        "Vehicles (Land)",
        "Vehicles (Sea)",
        "Weaver's tools",
        "Woodcarver's tools"
    )
    private val interactionCache: Cache<Snowflake, Snowflake> =
        Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build()

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("search", BuyProficiencyLocale.SEARCH_PARAMETER.locale("en")) {
            BuyProficiencyLocale.SEARCH_PARAMETER.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
    }

    override suspend fun registerCommand() {
        kord.on<ButtonInteractionCreateEvent> {
            val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
            if(interaction.componentId.startsWith("WaghamBuyProficiency") && interactionCache.getIfPresent(interaction.message.id) == interaction.user.id) {
                val response = interaction.deferPublicMessageUpdate()
                val guildId = interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
                val target = interaction.component.label ?: throw IllegalArgumentException(BuyProficiencyLocale.NO_PROFICIENCY_SELECTED.locale(locale))
                val itemCost = "1DayT2Badge"
                try {
                    val character = db.charactersScope.getActiveCharacter(guildId, interaction.user.id.toString())
                    val editedEmbed = when {
                        character.money < 250 -> createGenericEmbedError(CommonLocale.NOT_ENOUGH_MONEY.locale(locale))
                        character.inventory[itemCost] == null || character.inventory[itemCost]!! < 250 -> createGenericEmbedError(CommonLocale.NOT_ENOUGH_T2BADGE.locale(locale))
                        character.proficiencies.contains(target) -> createGenericEmbedError("${BuyProficiencyLocale.ALREADY_HAS_PROFICIENCY.locale(locale)}$target")
                        else -> {
                            val result = db.transaction(guildId) {
                                val moneyStep = db.charactersScope.subtractMoney(it, guildId, character.name, 250F)
                                val itemStep = db.charactersScope.removeItemFromInventory(it, guildId, character.name, itemCost, 250)
                                val proficiencyStep = db.charactersScope.addProficiencyToCharacter(it, guildId, character.name, target)
                                if (giveMoneyBadge.contains(target)) db.charactersScope.addProficiencyToCharacter(it, guildId, character.name, "ProficiencyMoneyBadge")
                                moneyStep && itemStep && proficiencyStep
                            }
                            if(result.committed) createGenericEmbedSuccess("${BuyProficiencyLocale.BUY_PROFICIENCY_SUCCESS.locale(locale)}$target")
                            else createGenericEmbedError(
                                "${CommonLocale.GENERIC_ERROR.locale(locale)}${result.exception?.message?.let { ": $it" } ?: ""}"
                            )
                        }
                    }
                    response.edit(editedEmbed)
                } catch (e: NoActiveCharacterException) {
                    response.edit(createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale)))
                }
            } else if (interaction.componentId.startsWith("WaghamBuyProficiency") && interactionCache.getIfPresent(interaction.message.id) == null) {
                interaction.deferPublicMessageUpdate().edit {
                    embed {
                        title = CommonLocale.INTERACTION_EXPIRED.locale(locale)
                        color = Colors.DEFAULT.value
                    }
                    components = mutableListOf()
                }
            } else if (interaction.componentId.startsWith("WaghamBuyProficiency")) {
                interaction.deferEphemeralResponse().respond(createGenericEmbedError(CommonLocale.INTERACTION_STARTED_BY_OTHER.locale(locale)))
            }
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val guildId = event.interaction.guildId
        val query = event.interaction.command.strings["search"]!!
        val options = cacheManager.getProficiencies(guildId)
            .filter { it.isPurchasable }
            .sortedBy { query.levenshteinDistance(it.name) }
            .reversed()
            .take(5)
        val ret: InteractionResponseModifyBuilder.() -> Unit = {
            embed {
                title = "${BuyProficiencyLocale.YOU_SEARCHED.locale(locale)} $query"
                description = "**${BuyProficiencyLocale.POSSIBLE_OPTIONS.locale(locale)}:**\n" + options.joinToString(separator = "\n") { it.name }
            }
            actionRow {
                options.forEach {
                    interactionButton(ButtonStyle.Secondary, "WaghamBuyProficiency-${it.name}") {
                        label = it.name
                    }
                }
            }
        }
        return ret
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        val msg = response.respond(builder)
        interactionCache.put(
            msg.message.id,
            event.interaction.user.id
        )
    }

}