package org.wagham.events

import dev.kord.core.Kord
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.wagham.annotations.BotEvent
import org.wagham.components.CacheManager
import org.wagham.db.KabotMultiDBClient
import org.wagham.utils.uniformProbability

@BotEvent("wagham")
class WaghamFunnyResponseEvent(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : RestrictedGuildEvent() {

    override val eventId = "wagham_funny_response_event"

    override fun register() {
        kord.on<MessageCreateEvent> {
            if(isEnabled(guildId) && isAllowed(guildId, message)) {
                when {
                    Regex("non +vedo +l.ora").matches(message.content.lowercase()) ->
                        "Sono le ${message.timestamp.toLocalDateTime(TimeZone.UTC).hour}:${message.timestamp.toLocalDateTime(TimeZone.UTC).minute}"
                    uniformProbability(80) && Regex("(falumo|falumia)").matches(message.content.lowercase()) ->
                        "Evviva Falumo!"
                    uniformProbability(60) && Regex("kairon").matches(message.content.lowercase()) ->
                        "Leccaterga!"
                    uniformProbability(40) && Regex("chiudi +la +bocca").matches(message.content.lowercase()) ->
                        "Zitto coglione!"
                    uniformProbability(15) && Regex("jack").matches(message.content.lowercase()) ->
                        "Mentecatto!"
                    uniformProbability(85) && Regex("ma +io +sono +l.eroe").matches(message.content.lowercase()) ->
                        "CLB è l'eroe!"
                    uniformProbability(85) && Regex("goblin +di +merda").matches(message.content.lowercase()) ->
                        "Sì, il goblin fa schifo!"
                    uniformProbability(85) && Regex("chi +mi +ha +colpito").matches(message.content.lowercase()) ->
                        "È stata l'Alta Stella!"
                    uniformProbability(75) && Regex("l.alta stella").matches(message.content.lowercase()) ->
                        "Quella buona e quella bella"
                    uniformProbability(50) && Regex("gilda +di +wagham").matches(message.content.lowercase()) ->
                        "Assassini diplomatici"
                    uniformProbability(80) && Regex("masterare").matches(message.content.lowercase()) && message.author?.id?.toString() == "493446767937978398" ->
                        message.content.replace("masterare", "~~masterare~~ **masterizzare**")
                    uniformProbability(80) && Regex("masterizzare").matches(message.content.lowercase()) && message.author?.id?.toString() == "493446767937978398" ->
                        message.content.replace("masterizzare", "~~masterizzare~~ **masterare**")
                    uniformProbability(1) && message.author?.id?.toString() == "165878899656491008" ->
                        ": pollice"
                    else -> null
                }?.let {
                    message.channel.createMessage(it)
                }
            }
        }
    }

}