package org.wagham.commands

import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.GuildChatInputCommandInteraction
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.RequestBuilder
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import kotlinx.coroutines.flow.firstOrNull
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.exceptions.UnauthorizedException

abstract class SlashCommand<T: RequestBuilder<*>> : Command<T> {

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
                            roles.forEach { append("<@&$it> ") }
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
                    try {
                        interaction.deferPublicResponse().respond {
                            embed {
                                title = "Error"
                                description = e.message ?: e.stackTraceToString()
                                color = Colors.ERROR.value
                            }
                        }
                    } catch (_: Exception) {
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

}