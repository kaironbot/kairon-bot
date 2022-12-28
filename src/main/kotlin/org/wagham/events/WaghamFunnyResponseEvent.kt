package org.wagham.events

import dev.kord.core.Kord
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.wagham.annotations.BotEvent
import org.wagham.components.CacheManager
import org.wagham.db.KabotMultiDBClient

@BotEvent
class WaghamFunnyResponseEvent(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : Event {

    override val name = "wagham_funny_response_event"
    override fun register() {
        kord.on<MessageCreateEvent> {
            when {
                Regex("non +vedo +l.ora").matches(message.content.lowercase()) ->
                    "Sono le ${message.timestamp.toLocalDateTime(TimeZone.UTC).hour}:${message.timestamp.toLocalDateTime(TimeZone.UTC).minute}"
                Regex("chiudi +la +bocca").matches(message.content.lowercase()) ->
                    "Zitto coglione!"
                Regex("jack").matches(message.content.lowercase()) ->
                    "Mentecatto!"
                Regex("ma +io +sono +l.eroe").matches(message.content.lowercase()) ->
                    "CLB è l'eroe!"
                Regex("goblin +di +merda").matches(message.content.lowercase()) ->
                    "Sì, il goblin fa schifo!"
                Regex("chi +mi +ha +colpito").matches(message.content.lowercase()) ->
                    "È stata l'Alta Stella!"
                Regex("l.alta stella").matches(message.content.lowercase()) ->
                    "Quella buona e quella bella"
                Regex("gilda +di +wagham").matches(message.content.lowercase()) ->
                    "Assassini diplomatici"
                else -> null
            }?.let {
                message.channel.createMessage(it)
            }
        }
    }

}