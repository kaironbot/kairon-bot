package org.wagham.events

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.edit
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.event.message.ReactionAddEvent
import dev.kord.core.on
import kotlinx.coroutines.flow.*
import org.wagham.annotations.BotEvent
import org.wagham.components.CacheManager
import org.wagham.config.Channels
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.CharacterStatus
import org.wagham.db.models.Character
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.sendTextMessage

@BotEvent("wagham")
class WaghamCheckTierEvent(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : RestrictedGuildEvent() {

    override val eventId = "register_session_check_tier"

    private val singleInsults = listOf("quel maramaldo", "quello zozzone", "quel maleducato", "quel maledetto", "quel manigoldo",
        "quel masnadiero", "quel pusillanime", "quell'infingardo", "quello svergognato", "quello scostumato", "quel mentecatto",
        "quell'illetterato", "quel furfante", "quell'onanista")

    override fun register() {
        kord.on<ReactionAddEvent> {
            if(isAllowed(guildId, message) && emoji.name == "🤖") {
                val guild = guildId ?: throw GuildNotFoundException()
                val serverConfig = cacheManager.getConfig(guild)
                db.charactersScope
                    .getAllCharacters(guildId.toString(), CharacterStatus.active)
                    .fold(Pair(emptyList<Snowflake>(), emptyList<Character>())) { acc, character ->
                        val tier = cacheManager.getExpTable(guild).expToTier(character.ms().toFloat())
                        val player = supplier.getMemberOrNull(guild, Snowflake(character.player))
                        if (player == null) {
                            acc.copy(second = acc.second + character)
                        } else if (player.roles.toList().all { !Regex("Tier $tier").matches(it.name) }) {
                            val updatedRoles = player.roles.filter { !Regex("Tier [0-9]").matches(it.name) }.map { it.id }.toList() +
                                supplier.getGuildRoles(guild).first { it.name == "Tier $tier" }.id
                            player.edit {
                                roles = updatedRoles.toMutableSet()
                            }
                            acc.copy(first = acc.first + player.id)
                        } else acc
                    }.let { updates ->
                        (serverConfig.channels[Channels.LOG_CHANNEL.name]
                            ?.let { Snowflake(it) }
                            ?.let {
                                supplier.getChannel(it).asChannelOf<MessageChannel>()
                            } ?: supplier.getGuild(guild).getSystemChannel())
                            ?.takeIf { updates.second.isNotEmpty() }
                            ?.sendTextMessage(
                                "WARNING: The following users do not exist\n${
                                    updates.second.joinToString(separator = "") { "(<@!${it.player}>) - ${it.name}\n" }
                                }"
                            )
                        updates.first
                    }.let { updates ->
                        if (updates.size == 1) {
                            "Dlin Dlon! ${singleInsults.random().replaceFirstChar { it.uppercase() }} di <@!${updates.first()}> è salito di tier. Congratulazioni!"
                        } else if (updates.size > 1) {
                            val concatenated = updates.subList(0, updates.size-1).joinToString { "${singleInsults.random()} di <@!${updates.first()}>" } +
                                    " e ${singleInsults.random()} di <@!${updates.last()}>"
                            "Dlin Dlon! ${concatenated.replaceFirstChar { it.uppercase() }} sono saliti di tier. Congratulazioni!"
                        } else null
                    }?.let { message ->
                        (serverConfig.channels[Channels.MESSAGE_CHANNEL.name]
                            ?.let { Snowflake(it) }
                            ?.let {
                                supplier.getChannel(it).asChannelOf<MessageChannel>()
                            } ?: supplier.getGuild(guild).getSystemChannel())
                        ?.sendTextMessage(message)
                    }
            }
        }
    }
}