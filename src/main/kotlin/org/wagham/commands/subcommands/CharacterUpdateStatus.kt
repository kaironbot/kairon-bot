package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.CharacterCommand
import org.wagham.components.CacheManager
import org.wagham.components.MultiCharacterCommand
import org.wagham.components.MultiCharacterManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.CharacterUpdateStatusLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.CharacterStatus
import org.wagham.db.models.Character
import org.wagham.entities.InteractionParameters
import org.wagham.utils.*

@BotSubcommand("all", CharacterCommand::class)
class CharacterUpdateStatus(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder>, MultiCharacterCommand<CharacterStatus> {

    companion object {
        private const val TARGET_ARGUMENT = "target"
        private const val STATUS_ARGUMENT = "status"
    }

    override val commandName = "update_status"
    override val defaultDescription = CharacterUpdateStatusLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = CharacterUpdateStatusLocale.DESCRIPTION.localeMap
    private val multiCharacterManager = MultiCharacterManager(db, kord, this)

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        user(TARGET_ARGUMENT, CharacterUpdateStatusLocale.TARGET.locale(defaultLocale)) {
            CharacterUpdateStatusLocale.TARGET.localeMap.forEach { (locale, description) ->
                description(locale, description)
            }
            required = true
            autocomplete = true
        }
        string(STATUS_ARGUMENT, CharacterUpdateStatusLocale.STATUS.locale(defaultLocale)) {
            CharacterUpdateStatusLocale.STATUS.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
            CharacterStatus.entries.forEach {
                choice(it.name, it.name)
            }
        }
    }

    override suspend fun registerCommand() { }

    override suspend fun multiCharacterAction(
        interaction: ComponentInteraction,
        characters: List<Character>,
        context: CharacterStatus,
        sourceCharacter: Character?
    ) {
        val params = interaction.extractCommonParameters()
        interaction.deferPublicMessageUpdate().edit(
            changeStatus(characters.first(), context, params)
        )
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        val target = event.interaction.command.users[TARGET_ARGUMENT] ?: throw IllegalStateException("Target not found")
        val status = event.interaction.command.strings[STATUS_ARGUMENT]?.let {
            CharacterStatus.valueOf(it)
        } ?: throw IllegalStateException("Status not found")
        guaranteeActiveCharacters(locale) {
            val targetOrSelectionContext = multiCharacterManager.startSelectionOrReturnCharacters(listOf(target), null, status, this)
            when {
                targetOrSelectionContext.characters != null -> changeStatus(targetOrSelectionContext.characters.first(), status, this)
                targetOrSelectionContext.response != null -> targetOrSelectionContext.response
                else -> createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))
            }
        }
    }

    private suspend fun changeStatus(character: Character, newStatus: CharacterStatus, params: InteractionParameters) = with(params) {
        val updateSuccess = db.charactersScope.updateCharacter(guildId.toString(), character.copy(status = newStatus))
        if (updateSuccess && newStatus != CharacterStatus.active) {
            db.playersScope.unsetActiveCharacter(guildId.toString(), character.player)
        }
        if (updateSuccess) createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
        else createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        response.respond(builder)
    }
}