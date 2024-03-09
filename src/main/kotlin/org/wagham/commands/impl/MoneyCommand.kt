package org.wagham.commands.impl

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.User
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.embed
import org.wagham.annotations.BotCommand
import org.wagham.commands.SimpleResponseSlashCommand
import org.wagham.components.CacheManager
import org.wagham.components.MultiCharacterCommand
import org.wagham.components.MultiCharacterManager
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.commands.MoneyLocale
import org.wagham.config.locale.components.MultiCharacterLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.Character
import org.wagham.utils.createGenericEmbedError
import org.wagham.utils.defaultLocale
import org.wagham.utils.extractCommonParameters
import org.wagham.utils.withOneActiveCharacterOrErrorMessage

@BotCommand("all")
class MoneyCommand(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SimpleResponseSlashCommand(), MultiCharacterCommand<User> {

    override val commandName = "money"
    override val defaultDescription = MoneyLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = MoneyLocale.DESCRIPTION.localeMap
    private val multiCharacterManager = MultiCharacterManager(db, kord, this)

    override suspend fun registerCommand() {
        kord.createGlobalChatInputCommand(
            commandName,
            defaultDescription
        ) {
            localeDescriptions.forEach{ (locale, description) ->
                description(locale, description)
            }
            user("target", MoneyLocale.TARGET.locale("en")) {
                MoneyLocale.TARGET.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = false
                autocomplete = true
            }
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val params = event.extractCommonParameters()
        return event.interaction.command.users["target"]?.takeIf { it.id != params.responsible.id }?.let {
            val targetOrSelectionContext = multiCharacterManager.startSelectionOrReturnCharacters(listOf(it), null, it, params)
            when {
                targetOrSelectionContext.characters != null -> generateMoneyEmbed(targetOrSelectionContext.characters.first(), it)
                targetOrSelectionContext.response != null -> targetOrSelectionContext.response
                else -> createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(params.locale))
            }
        } ?: withOneActiveCharacterOrErrorMessage(params.responsible, params) {
            generateMoneyEmbed(it, params.responsible)
        }
    }

    override suspend fun multiCharacterAction(
        interaction: ComponentInteraction,
        characters: List<Character>,
        context: User,
        sourceCharacter: Character?
    ) {
        val params = interaction.extractCommonParameters()
        val updateBehaviour = interaction.deferPublicMessageUpdate()
        when {
            characters.size != 1 -> createGenericEmbedError(MultiCharacterLocale.INVALID_TARGET_NUMBER.locale(params.locale))
            else -> generateMoneyEmbed(characters.first(), context)
        }.let { updateBehaviour.edit(it) }
    }

    private fun generateMoneyEmbed(character: Character, user: User) = fun InteractionResponseModifyBuilder.() {
        embed {
            color = Colors.DEFAULT.value
            author {
                name = character.name
                icon = user.avatar?.cdnUrl?.toUrl()
            }
            description = "${character.money} MO"
        }
        this.components = mutableListOf()
    }
}