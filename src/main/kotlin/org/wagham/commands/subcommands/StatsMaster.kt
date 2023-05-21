package org.wagham.commands.subcommands

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.Locale
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.User
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.actionRow
import dev.kord.rest.builder.message.modify.embed
import kotlinx.coroutines.flow.toList
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.impl.StatsCommand
import org.wagham.commands.SubCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.StatsMasterLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.pipelines.sessions.PlayerMasteredSessions
import org.wagham.entities.PaginatedList
import org.wagham.utils.createGenericEmbedError
import java.io.File
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path

@BotSubcommand("all", StatsCommand::class)
class StatsMaster(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "master"
    override val defaultDescription = "Buy a building"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Show the session mastered by you or by another player",
        Locale.ITALIAN to "Visualizza le sessioni masterate da te o da un altro giocatore"
    )
    private val interactionCache: Cache<Snowflake, Triple<Snowflake, User, PaginatedList<PlayerMasteredSessions>>> =
        Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build()
    private val tmpFolder = "src/main/resources/${this::class.simpleName}".also {
        if(!File(it).exists()) File(it).mkdirs()
    }

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        user("master", StatsMasterLocale.MASTER_PARAMETER.locale("en")) {
            StatsMasterLocale.MASTER_PARAMETER.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = false
            autocomplete = true
        }
    }

    override suspend fun registerCommand() {
        kord.on<ButtonInteractionCreateEvent> {
            val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
            if(interaction.componentId.startsWith("${this@StatsMaster::class.qualifiedName}") && interactionCache.getIfPresent(interaction.message.id)?.first == interaction.user.id) {
                when(interaction.componentId) {
                    "${this@StatsMaster::class.qualifiedName}-previous" -> {
                        val currentData = interactionCache.getIfPresent(interaction.message.id)!!
                        val newPage = currentData.third.previousPage()
                        interactionCache.put(interaction.message.id, Triple(currentData.first, currentData.second, newPage))
                        interaction.deferPublicMessageUpdate().edit(generateSessionEmbed(newPage, currentData.second, locale))
                    }
                    "${this@StatsMaster::class.qualifiedName}-next" -> {
                        val currentData = interactionCache.getIfPresent(interaction.message.id)!!
                        val newPage = currentData.third.nextPage()
                        interactionCache.put(interaction.message.id, Triple(currentData.first, currentData.second, newPage))
                        interaction.deferPublicMessageUpdate().edit(generateSessionEmbed(newPage, currentData.second, locale))
                    }
                    "${this@StatsMaster::class.qualifiedName}-export" -> {
                        val currentData = interactionCache.getIfPresent(interaction.message.id)!!
                        val filename = "session_report_${currentData.second.username}_${System.currentTimeMillis()}.csv"
                        Path(tmpFolder, filename).toFile().writeText(
                            currentData.third.toCSVFile()
                        )
                        interaction.user.getDmChannel().createMessage {
                            content = "Session report of ${currentData.second.username}"
                            addFile(Path(tmpFolder, filename))
                        }
                        interaction.deferEphemeralResponse().respond {
                            content = StatsMasterLocale.REPORT_TO_MP.locale(locale)
                        }
                        Path(tmpFolder, filename).toFile().delete()
                    }
                    else -> {
                        interaction.deferPublicMessageUpdate().edit(createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale)))
                    }
                }
            } else if (interaction.componentId.startsWith("${this@StatsMaster::class.qualifiedName}") && interactionCache.getIfPresent(interaction.message.id) == null) {
                interaction.deferEphemeralMessageUpdate().edit {
                    embed {
                        title = CommonLocale.INTERACTION_EXPIRED.locale(locale)
                        color = Colors.DEFAULT.value
                    }
                    components = mutableListOf()
                }
            } else if (interaction.componentId.startsWith("${this@StatsMaster::class.qualifiedName}")) {
                interaction.deferEphemeralResponse().respond(createGenericEmbedError(CommonLocale.INTERACTION_STARTED_BY_OTHER.locale(locale)))
            }
        }
    }

    private fun generateSessionEmbed(sessions: PaginatedList<PlayerMasteredSessions>, user: User, locale: String): InteractionResponseModifyBuilder.() -> Unit =
        fun InteractionResponseModifyBuilder.() {
            embed {
                title = "${StatsMasterLocale.SESSION_MASTERED_TITLE.locale(locale)}${user.username} ${sessions.size}"
                sessions.aggregateByCharacter().forEach {
                    field {
                        name = it.key
                        value = it.value.toString()
                        inline = true
                    }
                }
                field {
                    name = StatsMasterLocale.SESSION_LIST.locale(locale)
                    value = StatsMasterLocale.NAVIGATE.locale(locale)
                    inline = false
                }
                sessions.page.forEach {
                    field {
                        name = StatsMasterLocale.TITLE.locale(locale)
                        value = it.title
                        inline = true
                    }
                    field {
                        name = StatsMasterLocale.DATE.locale(locale)
                        value = SimpleDateFormat("dd/MM/yyyy").format(it.date)
                        inline = true
                    }
                    field {
                        name = "master"
                        value = it.master.split(":").last()
                        inline = true
                    }
                }
            }
            actionRow {
                interactionButton(ButtonStyle.Secondary, "${this@StatsMaster::class.qualifiedName}-previous") {
                    label = StatsMasterLocale.LABEL_PREVIOUS.locale(locale)
                }
                interactionButton(ButtonStyle.Secondary, "${this@StatsMaster::class.qualifiedName}-next") {
                    label = StatsMasterLocale.LABEL_NEXT.locale(locale)
                }
                interactionButton(ButtonStyle.Primary, "${this@StatsMaster::class.qualifiedName}-export") {
                    label = StatsMasterLocale.LABEL_EXPORT.locale(locale)
                }
            }
        }


    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val params = extractCommonParameters(event)
        val user = event.interaction.command.users["master"] ?: event.interaction.user
        val sessions = PaginatedList(db.sessionScope.getAllMasteredSessions(params.guildId.toString(), user.id.toString()).toList().sortedBy { it.date }.reversed())
        return if (!sessions.isEmpty()) {
            generateSessionEmbed(sessions, user, params.locale)
        } else {
            fun InteractionResponseModifyBuilder.() {
                embed {
                    title = "${StatsMasterLocale.NEVER_MASTERED_TITLE.locale(params.locale)}${user.username}"
                    description = StatsMasterLocale.NEVER_MASTERED_DESCRIPTION.locale(params.locale)
                }
            }
        }
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        val msg = response.respond(builder)
        val guildId = event.interaction.guildId
        val user = event.interaction.command.users["target"] ?: event.interaction.user
        val sessions = PaginatedList(db.sessionScope.getAllMasteredSessions(guildId.toString(), user.id.toString()).toList().sortedBy { it.date }.reversed())
        if(!sessions.isEmpty()) {
            interactionCache.put(
                msg.message.id,
                Triple(
                    event.interaction.user.id,
                    user,
                    sessions
                )
            )
        }
    }

    private fun PaginatedList<PlayerMasteredSessions>.aggregateByCharacter(): Map<String, Int> =
        this.elements.fold(mapOf()) { acc, it ->
            if(acc.containsKey(it.master)) {
                acc + (it.master to (acc[it.master]!! + 1))
            } else {
                acc + (it.master to 1)
            }
        }

    private fun PaginatedList<PlayerMasteredSessions>.toCSVFile(): String =
        "TITLE\tDATE\tGAME_DATE\tMASTER\n" +
                this.elements.joinToString(separator = "\n") {
                    "${it.title}\t" +
                            "${SimpleDateFormat("dd/MM/yyyy").format(it.date)}\t" +
                            "${it.gameDate.day}-${it.gameDate.month}-${it.gameDate.year},${it.gameDate.season}\t" +
                            it.master
                }

}
