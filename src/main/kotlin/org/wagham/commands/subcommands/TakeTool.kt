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
import org.wagham.commands.impl.TakeCommand
import org.wagham.components.CacheManager
import org.wagham.components.MultiCharacterCommand
import org.wagham.components.MultiCharacterManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.TakeToolLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.Character
import org.wagham.db.models.ToolProficiency
import org.wagham.db.models.embed.ProficiencyStub
import org.wagham.entities.InteractionParameters
import org.wagham.utils.*
import java.lang.IllegalStateException

@BotSubcommand("all", TakeCommand::class)
class TakeTool(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder>, MultiCharacterCommand<ToolProficiency> {

    override val commandName = "tool"
    override val defaultDescription = TakeToolLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = TakeToolLocale.DESCRIPTION.localeMap
    private val multiCharacterManager = MultiCharacterManager(db, kord, this)

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("tool", TakeToolLocale.TOOL.locale("en")) {
            TakeToolLocale.TOOL.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
        user("target", TakeToolLocale.TARGET.locale("en")) {
            TakeToolLocale.TARGET.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
            autocomplete = true
        }
    }

    override suspend fun registerCommand() {}

    override suspend fun multiCharacterAction(
        interaction: ComponentInteraction,
        characters: List<Character>,
        context: ToolProficiency,
        sourceCharacter: Character?
    ) {
        interaction.deferPublicMessageUpdate().edit(
            executeTransaction(characters.first(), context, interaction.extractCommonParameters())
        )
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        val target = event.interaction.command.users["target"] ?: throw IllegalStateException("Target not found")
        val tool = event.interaction.command.strings["tool"]
            ?.let { l ->
                cacheManager.getCollectionOfType<ToolProficiency>(guildId).firstOrNull {
                    it.name == l
                }
            } ?: throw IllegalStateException(TakeToolLocale.NOT_FOUND.locale(locale))
        val targetOrSelectionContext = multiCharacterManager.startSelectionOrReturnCharacters(listOf(target), null, tool, this)
        when {
            targetOrSelectionContext.characters != null -> executeTransaction(targetOrSelectionContext.characters.first(), tool, this)
            targetOrSelectionContext.response != null -> targetOrSelectionContext.response
            else -> createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))
        }
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        response.respond(builder)
    }

    private suspend fun executeTransaction(character: Character, tool: ToolProficiency, params: InteractionParameters) = with(params) {
        db.charactersScope.removeProficiencyFromCharacter(
            guildId.toString(),
            character.id,
            ProficiencyStub(tool.id, tool.name)
        ).let {
            when {
                it -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                else -> createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))
            }
        }
    }
}