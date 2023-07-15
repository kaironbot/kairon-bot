package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import org.wagham.annotations.BotCommand
import org.wagham.commands.SimpleResponseSlashCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.commands.ExpLocale
import org.wagham.config.locale.commands.PayLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Character

@BotCommand("all")
class ExpCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SimpleResponseSlashCommand() {

    override val commandName = "exp"
    override val defaultDescription = "Show your level and exp"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Show your level and MS",
        Locale.ITALIAN to "Mostra il tuo livello e la tua exp"
    )
    private val additionalUsers: Int = 5

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            defaultDescription
        ) {
            localeDescriptions.forEach{ (locale, description) ->
                description(locale, description)
            }
            user("target", ExpLocale.TARGET.locale("en")) {
                ExpLocale.TARGET.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = false
                autocomplete = true
            }
            (1 .. additionalUsers).forEach { paramIndex ->
                user("target-$paramIndex", PayLocale.ANOTHER_TARGET.locale("en")) {
                    PayLocale.ANOTHER_TARGET.localeMap.forEach{ (locale, description) ->
                        description(locale, description)
                    }
                    required = false
                    autocomplete = true
                }
            }
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val params = event.extractCommonParameters()
        val expTable = cacheManager.getExpTable(params.guildId)
        val (characters, inactives) = listOf(
            listOfNotNull(event.interaction.command.users["target"]?.id),
            (1 .. additionalUsers).mapNotNull { paramNum ->
                event.interaction.command.users["target-$paramNum"]?.id
            }
        ).flatten().toSet().fold(Pair<List<Character>, List<Snowflake>>(emptyList(), emptyList())) { acc, it ->
            try {
                val character = db.charactersScope.getActiveCharacter(params.guildId.toString(), it.toString())
                acc.copy(
                    first = acc.first + character
                )
            } catch (e: NoActiveCharacterException) {
                acc.copy(second = acc.second + it)
            }
        }
        return fun InteractionResponseModifyBuilder.() {
            embed {
                color = Colors.DEFAULT.value
                title = ExpLocale.TITLE.locale(params.locale)
                description = buildString {
                    append(ExpLocale.NO_ACTIVE_CHARACTERS.locale(params.locale))
                    append(" ")
                    append(inactives.joinToString(", ") { "<@!$it>"})
                }
                characters.forEach {
                    field {
                        name = "${it.name} (${it.characterClass})"
                        value = "<@!${it.player}>"
                        inline = false
                    }
                    field {
                        name = "Exp"
                        value = "${it.ms()}"
                        inline = true
                    }
                    field {
                        name = ExpLocale.LEVEL.locale(params.locale)
                        value = expTable.expToLevel(it.ms().toFloat())
                        inline = true
                    }
                    field {
                        name = "Tier"
                        value = expTable.expToTier(it.ms().toFloat())
                        inline = true
                    }
                }
            }
        }
    }

}