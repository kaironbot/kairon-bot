package org.wagham.commands

import dev.kord.core.Kord
import dev.kord.core.entity.interaction.SubCommand
import dev.kord.core.entity.interaction.response.PublicMessageInteractionResponse
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import mu.KotlinLogging
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.db.KabotMultiDBClient

abstract class SlashCommandWithSubcommands(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SlashCommand() {

    private val logger = KotlinLogging.logger {}
    private val subcommandsMap = autowireSubcommands().associateBy { it.subcommandName }

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            commandDescription
        ) {
            subcommandsMap.forEach {
                logger.info { "$commandName command - registered ${it.key} subcommand" }
                it.value.create(this)
            }
            subCommand("help", "Shows all the subcommands with their descriptions")
        }
        subcommandsMap.forEach {
            it.value.init()
        }
    }

    override fun handleResponse(msg: PublicMessageInteractionResponse, event: GuildChatInputCommandInteractionCreateEvent) {
        val subCommand = event.interaction.command as SubCommand
        subcommandsMap[subCommand.name]?.handleResponse(msg, event)
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val subCommand = event.interaction.command as SubCommand
        return subcommandsMap[subCommand.name]?.handle(event) ?: generateHelpMessage(event)
    }

    private fun generateHelpMessage(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val commandId = event.interaction.invokedCommandId.value
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        return fun InteractionResponseModifyBuilder.() {
            embed {
                color = Colors.DEFAULT.value
                title = "/$commandName"
                description = subcommandsMap.values
                    .joinToString(separator = "\n\n") {
                        "</$commandName ${it.subcommandName}:${commandId}> ${it.subcommandDescription[locale]}"
                    }


            }
            components = mutableListOf()
        }
    }


}