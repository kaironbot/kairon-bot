package org.wagham.commands.subcommands

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.User
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.AssignCommand
import org.wagham.components.CacheManager
import org.wagham.components.MultiCharacterCommand
import org.wagham.components.MultiCharacterManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.AssignToolLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.TransactionType
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Character
import org.wagham.db.models.ToolProficiency
import org.wagham.db.models.embed.ProficiencyStub
import org.wagham.db.models.embed.Transaction
import org.wagham.entities.InteractionParameters
import org.wagham.utils.*
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.TimeUnit

@BotSubcommand("all", AssignCommand::class)
class AssignTool(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder>, MultiCharacterCommand<ToolProficiency> {

    companion object {
        private data class AssignToolInteractionData(
            val responsible: Snowflake,
            val tool: ToolProficiency,
            val targetUser: User
        )
    }

    override val commandName = "tool"
    override val defaultDescription = AssignToolLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = AssignToolLocale.DESCRIPTION.localeMap
    private val multiCharacterManager = MultiCharacterManager(db, kord, this)
    private val interactionCache: Cache<String, AssignToolInteractionData> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build()

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("tool", AssignToolLocale.TOOL.locale("en")) {
            AssignToolLocale.TOOL.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
        user("target", AssignToolLocale.TARGET.locale("en")) {
            AssignToolLocale.TARGET.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
            autocomplete = true
        }
    }

    override suspend fun registerCommand() {
        kord.on<ButtonInteractionCreateEvent> {
            if (verifyId(interaction.componentId)) {
                val (id) = extractComponentsFromComponentId(interaction.componentId)
                val params = interaction.extractCommonParameters()
                val data = interactionCache.getIfPresent(id)
                when {
                    data == null -> interaction.respondWithExpirationError(params.locale)
                    data.responsible != params.responsible.id -> interaction.respondWithForbiddenError(params.locale)
                    else -> {
                        try {
                            val targetsOrSelectionContext = multiCharacterManager.startSelectionOrReturnCharacters(
                                listOf(data.targetUser),
                                null,
                                data.tool,
                                params
                            )
                            when {
                                targetsOrSelectionContext.characters != null -> assignToolProficiency(data.tool, targetsOrSelectionContext.characters.first().id, params)
                                targetsOrSelectionContext.response != null -> targetsOrSelectionContext.response
                                else -> createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(params.locale))
                            }
                        } catch (e: Exception) {
                            when(e) {
                                is NoActiveCharacterException -> createGenericEmbedError("<@!${e.playerId}> ${CommonLocale.NO_ACTIVE_CHARACTER.locale(params.locale)}")
                                else -> createGenericEmbedError(e.message ?: e.toString())
                            }
                        }.let {
                            interaction.deferPublicMessageUpdate().edit(it)
                        }
                    }
                }
            }
        }
    }

    private suspend fun assignToolProficiency(tool: ToolProficiency, target: String, params: InteractionParameters) =
        db.transaction(params.guildId.toString()) { s ->
            db.charactersScope.addProficiencyToCharacter(
                s,
                params.guildId.toString(),
                target,
                ProficiencyStub(tool.id, tool.name)
            ) && db.characterTransactionsScope.addTransactionForCharacter(
                s, params.guildId.toString(), target, Transaction(
                    Date(), null, "ASSIGN", TransactionType.ADD, mapOf(tool.name to 1f)
                )
            )
        }.let {
            when {
                it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(params.locale))
                else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
            }
        }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        val tools = cacheManager.getCollectionOfType<ToolProficiency>(guildId)
        val target = event.interaction.command.users["target"] ?: throw IllegalStateException("Target not found")
        val tool = event.interaction.command.strings["tool"] ?: throw IllegalStateException("Tool not found")
        return tools.firstOrNull { it.name == tool }?.let {
            val targetsOrSelectionContext = multiCharacterManager.startSelectionOrReturnCharacters(listOf(target), null, it, this)
            when {
                targetsOrSelectionContext.characters != null -> assignToolProficiency(it, targetsOrSelectionContext.characters.first().id, this)
                targetsOrSelectionContext.response != null -> targetsOrSelectionContext.response
                else -> createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))
            }
        } ?: tools.maxByOrNull { tool.levenshteinDistance(it.name) }?.let { probableTool ->
            val interactionId = compactUuid()
            interactionCache.put(
                interactionId,
                AssignToolInteractionData(responsible.id, probableTool, target)
            )
            alternativeOptionMessage(locale, tool, probableTool.name, buildElementId(interactionId))
        } ?: createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))
    }

    override suspend fun multiCharacterAction(
        interaction: ComponentInteraction,
        characters: List<Character>,
        context: ToolProficiency,
        sourceCharacter: Character?
    ) {
        interaction.deferPublicMessageUpdate().edit(
            assignToolProficiency(context, characters.first().id, interaction.extractCommonParameters())
        )
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        response.respond(builder)
    }

}