package org.wagham.commands

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.GuildChatInputCommandInteraction
import dev.kord.core.entity.interaction.response.PublicMessageInteractionResponse
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.message.modify.embed
import org.wagham.config.Colors
import org.wagham.exceptions.CallerNotFoundException
import org.wagham.exceptions.GuildOwnerNotFoundException

abstract class SlashCommand : Command {

    suspend fun isUserAuthorized(guildId: Snowflake, interaction: GuildChatInputCommandInteraction, roles: Collection<Snowflake>): Boolean {
        val ownerId = kord.getGuildOrNull(guildId)?.ownerId ?: throw GuildOwnerNotFoundException()
        val caller = interaction.data.member.value ?: throw CallerNotFoundException()
        return caller.userId == ownerId || roles.any{ caller.roles.contains(it) }
    }

    override suspend fun handleResponse(
        msg: PublicMessageInteractionResponse,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {}

    override fun registerCallback() {
        kord.on<GuildChatInputCommandInteractionCreateEvent> {
            if (interaction.invokedCommandName == commandName) {
                val response = interaction.deferPublicResponse()
                try {
                    val sentMsg = response.respond(execute(this))
                    handleResponse(sentMsg, this)
                } catch (e: Exception) {
                    response.respond {
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