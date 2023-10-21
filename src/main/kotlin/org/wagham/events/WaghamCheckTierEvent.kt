package org.wagham.events

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Member
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.event.message.ReactionAddEvent
import dev.kord.core.on
import dev.kord.core.supplier.EntitySupplier
import kotlinx.coroutines.flow.*
import org.wagham.annotations.BotEvent
import org.wagham.components.CacheManager
import org.wagham.config.Channels
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.CharacterStatus
import org.wagham.db.models.Character
import org.wagham.entities.channels.UpdateGuildAttendanceMessage
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

    private suspend fun updateUserRoles(player: Member, guildId: Snowflake, db: KabotMultiDBClient, supplier: EntitySupplier) {
        val expTable = cacheManager.getExpTable(guildId)
        val tiers = db.charactersScope.getActiveCharacters(guildId.toString(), player.id.toString()).map {
            expTable.expToTier(it.ms().toFloat())
        }
        val updatedRoles = player.roles.filter { !Regex("Tier [0-9]").matches(it.name) }.map { it.id }.toList() +
                tiers.map { tier -> supplier.getGuildRoles(guildId).first { it.name == "Tier $tier" }.id }.toList()
        player.edit {
            roles = updatedRoles.toMutableSet()
        }
    }

    override fun register() {
        kord.on<ReactionAddEvent> {
            if(isEnabled(guildId) && isAllowed(guildId, message) && emoji.name == "🤖") {
                val guild = guildId ?: throw GuildNotFoundException()
                cacheManager.sendToChannel<DailyAttendanceEvent, UpdateGuildAttendanceMessage>(
                    UpdateGuildAttendanceMessage(guild)
                )
                val serverConfig = cacheManager.getConfig(guild)
                db.charactersScope
                    .getAllCharacters(guildId.toString(), CharacterStatus.active)
                    .fold(Pair(emptyList<Pair<Member, Character>>(), emptyList<Character>())) { acc, character ->
                        val tier = cacheManager.getExpTable(guild).expToTier(character.ms().toFloat())
                        val player = supplier.getMemberOrNull(guild, Snowflake(character.player))
                        if (player == null) {
                            acc.copy(second = acc.second + character)
                        } else if (player.roles.toList().all { !Regex("Tier $tier").matches(it.name) }) {
                            acc.copy(first = acc.first + (player to character))
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
                        updates.map { it.first }.toSet().forEach {
                            updateUserRoles(it, guild, db, supplier)
                        }
                        if (updates.size == 1) {
                            "Dlin Dlon! ${singleInsults.random().replaceFirstChar { it.uppercase() }} di ${updates.first().second.name} (<@!${updates.first().first.id}>) è salito di tier. Congratulazioni!"
                        } else if (updates.size > 1) {
                            val concatenated = updates.subList(0, updates.size-1).joinToString { "${singleInsults.random()} di ${it.second.name} (<@!${it.first.id}>)" } +
                                    " e ${singleInsults.random()} di ${updates.last().second.name} (<@!${updates.last().first.id}>)"
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