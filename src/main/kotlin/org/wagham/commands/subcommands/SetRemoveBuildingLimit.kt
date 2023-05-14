package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.SetCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.SetBuildingLimitLocale
import org.wagham.config.locale.subcommands.SetRemoveBuildingLimitLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.BuildingRestrictionType
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.createGenericEmbedSuccess
import java.lang.IllegalStateException

@BotSubcommand("all", SetCommand::class)
class SetRemoveBuildingLimit(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {


    override val commandName = "remove_building_limit"
    override val defaultDescription = "Remove a limit on the maximum number of buildings a player can have"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Remove a limit on the maximum number of buildings a player can have",
        Locale.ITALIAN to "Rimuove un limite sul massimo numero di edifici che un giocatore può avere"
    )

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("limit_type", SetRemoveBuildingLimitLocale.LIMIT_TYPE.locale("en")) {
            SetRemoveBuildingLimitLocale.LIMIT_TYPE.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            BuildingRestrictionType.values().forEach {
                choice(it.name, it.name)
            }
            required = true
        }
    }

    override suspend fun registerCommand() {}

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value ?: throw GuildNotFoundException()
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val limitType = event.interaction.command.strings["limit_type"]?.let {
            BuildingRestrictionType.valueOf(it)
        } ?: throw IllegalStateException("Limit type not found")
        val config = cacheManager.getConfig(guildId)
        cacheManager.setConfig(
            guildId,
            config.copy(
                buildingRestrictions = config.buildingRestrictions +
                        (limitType to null)
            )
        )
        return createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        response.respond(builder)
    }


}