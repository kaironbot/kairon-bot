package org.wagham.commands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.SubCommand
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.RequestBuilder
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import mu.KotlinLogging
import org.reflections.Reflections
import org.wagham.annotations.BotSubcommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.db.KabotMultiDBClient
import kotlin.reflect.full.primaryConstructor

abstract class SlashCommandWithSubcommands(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SlashCommand<RequestBuilder<Any>>() {

    private val logger = KotlinLogging.logger {}
    private val subcommandsMap = autowireSubcommands().associateBy { it.commandName }

    private fun autowireSubcommands() = Reflections("org.wagham.commands.subcommands")
        .getTypesAnnotatedWith(BotSubcommand::class.java)
        .map { it.kotlin }
        .filter {
            it.annotations.any { ann ->
                ann is BotSubcommand
                        && (ann.profile == "all" || ann.profile == cacheManager.profile)
                        && (ann.baseCommand == this::class)
            }
        }
        .map {
            it.primaryConstructor!!.call(kord, cacheManager.db, cacheManager)
                    as org.wagham.commands.SubCommand<RequestBuilder<Any>>
        }

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            defaultDescription
        ) {
            localeDescriptions.forEach{ (locale, description) ->
                description(locale, description)
            }
            subcommandsMap.forEach {
                logger.info { "$commandName command - registered ${it.key} subcommand" }
                it.value.create(this)
            }
            subCommand("help", "Shows all the subcommands with their descriptions") {
                description(Locale.ITALIAN, "Mostra tutte le possibili opzioni")
            }
        }
        subcommandsMap.forEach {
            it.value.registerCommand()
        }
    }

    override suspend fun handleResponse(builder: RequestBuilder<Any>.() -> Unit, event: GuildChatInputCommandInteractionCreateEvent) {
        val subCommand = event.interaction.command as SubCommand
        subcommandsMap[subCommand.name]?.handleResponse(builder, event)
            ?: event.interaction.deferPublicResponse().respond(builder)
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): RequestBuilder<Any>.() -> Unit {
        val subCommand = event.interaction.command as SubCommand
        return subcommandsMap[subCommand.name]?.execute(event) ?: generateHelpMessage(event)
    }

    private fun generateHelpMessage(event: GuildChatInputCommandInteractionCreateEvent): RequestBuilder<Any>.() -> Unit {
        val commandId = event.interaction.invokedCommandId.value
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        return fun InteractionResponseModifyBuilder.() {
            embed {
                color = Colors.DEFAULT.value
                title = "/$commandName"
                description = subcommandsMap.values
                    .joinToString(separator = "\n\n") {
                        "</$commandName ${it.commandName}:${commandId}> ${it.localeDescriptions[Locale.fromString(locale)] ?: it.defaultDescription}"
                    }
            }
            components = mutableListOf()
        } as RequestBuilder<Any>.() -> Unit
    }


}