package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.SetCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.SetBuildingLimitLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.BuildingRestrictionType
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.createGenericEmbedSuccess
import java.lang.IllegalStateException

@BotSubcommand("all", SetCommand::class)
class SetBuildingLimit(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {


    override val commandName = "building_limit"
    override val defaultDescription = "Set a limit on the maximum number of buildings a player can have"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Set a limit on the maximum number of buildings a player can have",
        Locale.ITALIAN to "Imposta il numero massimo di edifici che un giocatore può avere"
    )

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("limit_type", SetBuildingLimitLocale.LIMIT_TYPE.locale("en")) {
            SetBuildingLimitLocale.LIMIT_TYPE.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            BuildingRestrictionType.values().forEach {
                choice(it.name, it.name)
            }
            required = true
        }
        integer("limit", SetBuildingLimitLocale.LIMIT.locale("en")) {
            SetBuildingLimitLocale.LIMIT.localeMap.forEach{ (locale, description) ->
                description(locale, description)
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
        val limit = event.interaction.command.integers["limit"]?.takeIf { it >= 0 }?.toInt()
            ?: throw IllegalStateException(SetBuildingLimitLocale.INVALID_VALUE.locale(locale))
        val config = cacheManager.getConfig(guildId)
        cacheManager.setConfig(
            guildId,
            config.copy(
                buildingRestrictions = config.buildingRestrictions +
                    (limitType to limit)
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