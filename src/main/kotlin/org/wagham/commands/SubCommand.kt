package org.wagham.commands

import dev.kord.core.Kord
import dev.kord.core.entity.interaction.response.PublicMessageInteractionResponse
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.commands.Command
import org.wagham.components.CacheManager
import org.wagham.db.KabotMultiDBClient

interface SubCommand : Command {

    val subcommandDescription: Map<String, String>

    fun create(ctx: RootInputChatBuilder)

    override fun registerCallback() { }

}