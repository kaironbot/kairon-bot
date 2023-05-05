package org.wagham.commands.subcommands

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.Locale
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.actionRow
import dev.kord.rest.builder.message.modify.embed
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.AssignCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.AssignLanguageLocale
import org.wagham.config.locale.subcommands.AssignToolLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.LanguageProficiency
import org.wagham.db.models.ToolProficiency
import org.wagham.db.models.embed.ProficiencyStub
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.createGenericEmbedError
import org.wagham.utils.createGenericEmbedSuccess
import org.wagham.utils.levenshteinDistance
import java.lang.IllegalStateException
import java.util.concurrent.TimeUnit

@BotSubcommand("all", AssignCommand::class)
class AssignTool(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "tool"
    override val defaultDescription = "Assign a tool proficiency to a player"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Assign a tool proficiency to a player",
        Locale.ITALIAN to "Assegna la competenza in uno strumento a un giocatore"
    )
    private val interactionCache: Cache<Snowflake, Pair<Snowflake, Snowflake>> =
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
            val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
            if(interaction.componentId.startsWith("${this@AssignTool::class.qualifiedName}") && interactionCache.getIfPresent(interaction.message.id)?.first == interaction.user.id) {
                val guildId = interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
                val target = interactionCache.getIfPresent(interaction.message.id)?.second ?: throw IllegalStateException("Cannot find targets")
                val tool = Regex("${this@AssignTool::class.qualifiedName}-([0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12})")
                    .find(interaction.componentId)
                    ?.groupValues
                    ?.get(1)
                    ?: throw IllegalStateException("Cannot parse parameters")
                val tools = cacheManager.getCollectionOfType<ToolProficiency>(guildId)
                val character = db.charactersScope.getActiveCharacter(guildId, target.toString())
                assignToolProficiency(guildId, tools.first { it.id == tool }, character.id).let {
                    when {
                        it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                        it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                        else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
                    }
                }.let {
                    interaction.deferPublicMessageUpdate().edit(it)
                }
            } else if (interaction.componentId.startsWith("${this@AssignTool::class.qualifiedName}") && interactionCache.getIfPresent(interaction.message.id) == null) {
                interaction.deferEphemeralMessageUpdate().edit(createGenericEmbedError(CommonLocale.INTERACTION_EXPIRED.locale(locale)))
            } else if (interaction.componentId.startsWith("${this@AssignTool::class.qualifiedName}")) {
                interaction.deferEphemeralResponse().respond(createGenericEmbedError(CommonLocale.INTERACTION_STARTED_BY_OTHER.locale(locale)))
            }
        }
    }

    private suspend fun assignToolProficiency(guildId: String, tool: ToolProficiency, target: String) =
        db.transaction(guildId) { s ->
            db.charactersScope.addProficiencyToCharacter(
                s,
                guildId,
                target,
                ProficiencyStub(tool.id, tool.name)
            )
        }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val tools = cacheManager.getCollectionOfType<ToolProficiency>(guildId)
        val target = event.interaction.command.users["target"]?.id ?: throw IllegalStateException("Target not found")
        val tool = event.interaction.command.strings["tool"] ?: throw IllegalStateException("Tool not found")
        return try {
            if (tools.firstOrNull { it.name == tool } == null) {
                val probableTool = tools.maxByOrNull { tool.levenshteinDistance(it.name) }
                fun InteractionResponseModifyBuilder.() {
                    embed {
                        title = CommonLocale.ERROR.locale(locale)
                        description = buildString {
                            append(AssignToolLocale.NOT_FOUND.locale(locale))
                            append(tool)
                            probableTool?.also {
                                append("\n")
                                append(AssignToolLocale.ALTERNATIVE.locale(locale))
                                append(it.name)
                            }
                        }
                        color = Colors.DEFAULT.value
                    }
                    probableTool?.also {
                        actionRow {
                            interactionButton(ButtonStyle.Primary, "${this@AssignTool::class.qualifiedName}-${it.id}") {
                                label = "${AssignLanguageLocale.ASSIGN_ALTERNATIVE.locale(locale)} ${it.name}"
                            }
                        }
                    }
                }
            } else {
                val character = db.charactersScope.getActiveCharacter(guildId, target.toString())
                assignToolProficiency(guildId, tools.first { it.name == tool }, character.id).let {
                    when {
                        it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                        it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                        else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
                    }
                }
            }
        } catch (e: NoActiveCharacterException) {
            createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
        }
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        val msg = response.respond(builder)
        interactionCache.put(
            msg.message.id,
            Pair(
                event.interaction.user.id,
                event.interaction.command.users["target"]!!.id
            )
        )
    }

}