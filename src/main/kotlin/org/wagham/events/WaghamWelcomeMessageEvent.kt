package org.wagham.events

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.event.guild.MemberJoinEvent
import dev.kord.core.on
import kotlinx.coroutines.flow.firstOrNull
import org.wagham.annotations.BotEvent
import org.wagham.components.CacheManager
import org.wagham.config.Channels
import org.wagham.db.KabotMultiDBClient
import org.wagham.utils.sendTextMessage

@BotEvent("wagham")
class WaghamWelcomeMessageEvent(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : Event {

    override val eventId = "wagham_welcome_message"
    override fun register() {
        kord.on<MemberJoinEvent> {
            if(cacheManager.getConfig(this.getGuild().id).eventChannels[eventId]?.enabled == true) {
                this.getGuild()
                    .roles
                    .firstOrNull { it.name == "Aspirante Giocatore" }
                    ?.let {
                        this.member.addRole(it.id)
                    }
                cacheManager.getConfig(guildId).channels[Channels.WELCOME_CHANNEL.name]
                    ?.let {
                        this.getGuild().getChannel(Snowflake(it)).asChannelOf<MessageChannel>()
                            .sendTextMessage(buildString {
                                append("Benvenuto, <@${this@on.member.id}> in Tales from Ivory!\n")
                                append("Consulta il <#1099391049278959647>! ")
                                append("L'<@&1099408245136822334> ti contatterà appena possibile per assisterti nella creazione del personaggio e per orientarti nel server.\n")
                                append("Buon divertimento!")
                            })
                    }
            }
        }
    }

}