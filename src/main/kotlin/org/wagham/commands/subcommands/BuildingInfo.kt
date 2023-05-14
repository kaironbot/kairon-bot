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
import org.wagham.commands.impl.BuildingCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.commands.GiveLocale
import org.wagham.config.locale.subcommands.BuildingInfoLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.pipelines.buildings.BuildingWithBounty
import org.wagham.utils.levenshteinDistance
import java.lang.IllegalStateException

@BotSubcommand("all", BuildingCommand::class)
class BuildingInfo(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "info"
    override val defaultDescription = "Show details about a building"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Show details about a building",
        Locale.ITALIAN to "Mostra le informazioni riguardo un edificio"
    )

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach { (locale, description) ->
            description(locale, description)
        }
        string("building", GiveLocale.ITEM.locale("en")) {
            BuildingInfoLocale.BUILDING.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
    }

    override suspend fun registerCommand() { }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.guildId
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val buildings = cacheManager.getCollectionOfType<BuildingWithBounty>(guildId)
        val query = event.interaction.command.strings["building"] ?: throw IllegalStateException("Building not provided")
        val config = cacheManager.getConfig(guildId)
        val building = buildings.firstOrNull { it.name == query }
            ?: buildings.maxByOrNull { query.levenshteinDistance(it.name) }
            ?: throw IllegalStateException("${BuildingInfoLocale.BUILDING_NOT_FOUND.locale(locale)}: $query")
        val character = try {
            db.charactersScope.getActiveCharacter(guildId.toString(), event.interaction.user.id.toString())
        } catch (_: NoActiveCharacterException) { null }
        return fun InteractionResponseModifyBuilder.() {
            embed(BuildingCommand.describeBuildingMessage(building, locale, config, character))
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