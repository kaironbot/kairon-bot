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
import org.wagham.config.locale.subcommands.SetConfigOptionLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.ServerConfig
import org.wagham.utils.createGenericEmbedSuccess
import org.wagham.utils.extractCommonParameters
import java.lang.IllegalStateException

@BotSubcommand("all", SetCommand::class)
class SetConfigOption(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {


    override val commandName = "config_option"
    override val defaultDescription = "Sets the optional config for this server"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to defaultDescription,
        Locale.ITALIAN to "Imposta le configurazioni opzionali per questo server"
    )

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("config_type", SetConfigOptionLocale.CONFIG_TYPE.locale("en")) {
            SetConfigOptionLocale.CONFIG_TYPE.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            ServerConfig.Companion.PlayerConfigurations.values().forEach {
                choice(it.name, it.name)
            }
            required = true
        }
        boolean("value", SetConfigOptionLocale.VALUE.locale("en")) {
            SetConfigOptionLocale.VALUE.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
    }

    override suspend fun registerCommand() {}

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val params = event.extractCommonParameters()
        val limitType = event.interaction.command.strings["config_type"]?.let {
            ServerConfig.Companion.PlayerConfigurations.valueOf(it)
        } ?: throw IllegalStateException("Option type not found")
        val propertyValue = event.interaction.command.booleans["value"]
            ?: throw IllegalStateException("value not found")
        val config = cacheManager.getConfig(params.guildId)
        cacheManager.setConfig(
            params.guildId,
            config.copy(
                playerConfigurations = config.playerConfigurations +
                    (limitType to propertyValue)
            )
        )
        return createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(params.locale))
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        response.respond(builder)
    }
}