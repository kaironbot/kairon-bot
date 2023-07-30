package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.role
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotCommand
import org.wagham.commands.SimpleResponseSlashCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.commands.SetAdminGroupLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.exceptions.*
import org.wagham.utils.createGenericEmbedSuccess
import org.wagham.utils.defaultLocale

@BotCommand("all")
class SetAdminGroupCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SimpleResponseSlashCommand() {

    override val commandName = "set_admin_role"
    override val defaultDescription = SetAdminGroupLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = SetAdminGroupLocale.DESCRIPTION.localeMap

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            defaultDescription
        ) {
            localeDescriptions.forEach{ (locale, description) ->
                description(locale, description)
            }
            role("role", SetAdminGroupLocale.ROLE.locale("en")) {
                SetAdminGroupLocale.ROLE.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                autocomplete = true
                required = true
            }
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value ?: throw GuildNotFoundException()
        val serverConfig = cacheManager.getConfig(guildId, true)
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        return event.interaction.command.roles["role"]?.let {
            cacheManager.setConfig(
                guildId,
                serverConfig.copy(adminRoleId = it.id.toString())
            )
            createGenericEmbedSuccess("${SetAdminGroupLocale.CURRENT_ROLE.locale(locale)} <@&${it.id}>")
        } ?: throw InvalidCommandArgumentException()
    }

}