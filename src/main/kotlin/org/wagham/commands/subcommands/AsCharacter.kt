package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.AsCommand
import org.wagham.components.CacheManager
import org.wagham.components.MultiCharacterCommand
import org.wagham.components.MultiCharacterManager
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.AsCharacterLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.Character
import org.wagham.utils.*

@BotSubcommand("all", AsCommand::class)
class AsCharacter(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder>, MultiCharacterCommand<Unit> {

    override val commandName = "character"
    override val defaultDescription = AsCharacterLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = AsCharacterLocale.DESCRIPTION.localeMap
    private val multiCharacterManager = MultiCharacterManager(db, kord, this)

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
    }

    override suspend fun registerCommand() { }

    override suspend fun multiCharacterAction(
        interaction: ComponentInteraction,
        characters: List<Character>,
        context: Unit,
        sourceCharacter: Character?
    ) {
        val params = interaction.extractCommonParameters()
        val character = characters.first()
        db.playersScope.setActiveCharacter(params.guildId.toString(), character.player, character.id).let { result ->
            if(result) {
                fun InteractionResponseModifyBuilder.() {
                    embed {
                        title = CommonLocale.SUCCESS.locale(params.locale)
                        description = "${AsCharacterLocale.TITLE.locale(params.locale)} ${character.name}"
                        color = Colors.DEFAULT.value
                    }
                    components = mutableListOf()
                }
            } else createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(params.locale))
        }.let {
            interaction.deferPublicMessageUpdate().edit(it)
        }
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        guaranteeActiveCharacters(locale) {
            val targetOrSelectionContext = multiCharacterManager.startSelectionOrReturnCharacters(listOf(responsible), null, Unit, this)
            when {
                targetOrSelectionContext.characters != null -> fun InteractionResponseModifyBuilder.() {
                    embed {
                        title = "${AsCharacterLocale.TITLE.locale(locale)} ${targetOrSelectionContext.characters.first().name}"
                        description = AsCharacterLocale.ONE_CHARACTER.locale(locale)
                        color = Colors.DEFAULT.value
                    }
                }
                targetOrSelectionContext.response != null -> targetOrSelectionContext.response
                else -> createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))
            }
        }
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        response.respond(builder)
    }
}