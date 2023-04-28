package org.wagham.commands

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.entity.interaction.GuildChatInputCommandInteraction
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.RequestBuilder
import dev.kord.rest.builder.message.create.embed
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.exceptions.CallerNotFoundException
import org.wagham.exceptions.GuildOwnerNotFoundException
import org.wagham.exceptions.UnauthorizedException

abstract class SlashCommand<T: RequestBuilder<*>> : Command<T> {

    suspend fun isUserAuthorized(guildId: Snowflake, interaction: GuildChatInputCommandInteraction, roles: Collection<Snowflake>): Boolean {
        val ownerId = kord.getGuildOrNull(guildId)?.ownerId ?: throw GuildOwnerNotFoundException()
        val caller = interaction.data.member.value ?: throw CallerNotFoundException()
        return caller.userId == ownerId || roles.any{ caller.roles.contains(it) }
    }

    private suspend fun stopIfUnauthorized(interaction: GuildChatInputCommandInteraction) {
        val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
        val serverConfig = cacheManager.getConfig(interaction.guildId)
        serverConfig.commandsPermissions[interaction.invokedCommandName]
            ?.takeIf { it.isNotEmpty() }
            ?.let { roles ->
                if(interaction.user.roles.firstOrNull {
                    roles.contains(it.id.toString()) || serverConfig.adminRoleId == it.id.toString()
                } == null)
                    throw UnauthorizedException(
                        buildString {
                            append(CommonLocale.UNAUTHORIZED.locale(locale))
                            roles.forEach { append("<@%$it> ") }
                        }
                    )
            }
    }

    override fun registerCallback() {
        kord.on<GuildChatInputCommandInteractionCreateEvent> {
            if (interaction.invokedCommandName == commandName) {
                try {
                    stopIfUnauthorized(interaction)
                    val builder = execute(this)
                    handleResponse(builder, this)
                } catch (e: Exception) {
                   interaction.channel.createMessage {
                       embed {
                           title = "Error"
                           description = e.message ?: e.stackTraceToString()
                           color = Colors.ERROR.value
                       }
                    }
                }
            }
        }
    }

}