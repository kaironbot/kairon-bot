package org.wagham.commands.subcommands

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.User
import dev.kord.core.entity.interaction.response.PublicMessageInteractionResponse
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.NamedFile
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.actionRow
import dev.kord.rest.builder.message.modify.embed
import io.ktor.client.request.forms.*
import kotlinx.coroutines.flow.toList
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.StatsCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.LocaleEnum
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.pipelines.sessions.PlayerMasteredSessions
import org.wagham.entities.PaginatedList
import org.wagham.utils.createGenericEmbedError
import java.io.File
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path

@BotSubcommand("all", StatsCommand::class)
class MasterStats(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand {

    companion object {
        private enum class MasterStarsLocale(private val localeMap: Map<String, String>) : LocaleEnum {
            DATE(
                mapOf(
                    "en" to "Date",
                    "it" to "Data"
                )
            ),
            LABEL_EXPORT(
                mapOf(
                    "en" to "Export",
                    "it" to "Esporta"
                )
            ),
            LABEL_NEXT(
                mapOf(
                    "en" to "Next",
                    "it" to "Successivo"
                )
            ),
            LABEL_PREVIOUS(
                mapOf(
                    "en" to "Previous",
                    "it" to "Precedente"
                )
            ),
            NAVIGATE(
                mapOf(
                    "en" to "Use the buttons to navigate or export the list",
                    "it" to "Usa i pulsanti per navigare o per esportare la lista"
                )
            ),
            NEVER_MASTERED_TITLE(
                mapOf(
                    "en" to "This player has never mastered: ",
                    "it" to "Questo giocatore non ha mai masterato: "
                )
            ),
            NEVER_MASTERED_DESCRIPTION(
                mapOf(
                    "en" to "Start mastering now!",
                    "it" to "Comincia a masterare ora!"
                )
            ),
            REPORT_TO_MP(
                mapOf(
                    "en" to "The report of the sessions will be sent to you in DM",
                    "it" to "Il report delle sessioni ti verrà inviato tramite messaggio privato"
                )
            ),
            SESSION_LIST(
                mapOf(
                    "en" to "Session list:",
                    "it" to "Elenco delle sessioni:"
                )
            ),
            SESSION_MASTERED_TITLE(
              mapOf(
                  "en" to "Sessions mastered by ",
                  "it" to "Sessioni masterate da "
              )
            ),
            TITLE(
                mapOf(
                    "en" to "Title",
                    "it" to "Titolo"
                )
            );

            override fun locale(language: String) = localeMap[language] ?: localeMap["en"]!!
        }
    }

    override val subcommandName = "master"
    override val subcommandDescription: Map<String, String> = mapOf(
        "it" to "Visualizza le sessioni masterate da te o da un altro giocatore",
        "en" to "Show the session mastered by you or by another player"
    )
    private val interactionCache: Cache<Snowflake, Triple<Snowflake, User, PaginatedList<PlayerMasteredSessions>>> =
        Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build()
    private val tmpFolder = "src/main/resources/${this::class.simpleName}".also {
        if(!File(it).exists()) File(it).mkdirs()
    }

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(subcommandName, "Show the session mastered by you or by another player") {
        user("master", "The user to show the stats for") {
            required = false
            autocomplete = true
        }
    }

    override suspend fun init() {
        kord.on<ButtonInteractionCreateEvent> {
            val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
            if(interaction.componentId.startsWith("${this@MasterStats::class.qualifiedName}") && interactionCache.getIfPresent(interaction.message.id)?.first == interaction.user.id) {
                when(interaction.componentId) {
                    "${this@MasterStats::class.qualifiedName}-previous" -> {
                        val currentData = interactionCache.getIfPresent(interaction.message.id)!!
                        val newPage = currentData.third.previousPage()
                        interactionCache.put(interaction.message.id, Triple(currentData.first, currentData.second, newPage))
                        interaction.deferPublicMessageUpdate().edit(generateSessionEmbed(newPage, currentData.second, locale))
                    }
                    "${this@MasterStats::class.qualifiedName}-next" -> {
                        val currentData = interactionCache.getIfPresent(interaction.message.id)!!
                        val newPage = currentData.third.nextPage()
                        interactionCache.put(interaction.message.id, Triple(currentData.first, currentData.second, newPage))
                        interaction.deferPublicMessageUpdate().edit(generateSessionEmbed(newPage, currentData.second, locale))
                    }
                    "${this@MasterStats::class.qualifiedName}-export" -> {
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
                            content = MasterStarsLocale.REPORT_TO_MP.locale(locale)
                        }
                        Path(tmpFolder, filename).toFile().delete()
                    }
                    else -> {
                        interaction.deferPublicMessageUpdate().edit(createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale)))
                    }
                }
            } else if (interaction.componentId.startsWith("${this@MasterStats::class.qualifiedName}") && interactionCache.getIfPresent(interaction.message.id) == null) {
                interaction.deferEphemeralMessageUpdate().edit {
                    embed {
                        title = CommonLocale.INTERACTION_EXPIRED.locale(locale)
                        color = Colors.DEFAULT.value
                    }
                    components = mutableListOf()
                }
            } else if (interaction.componentId.startsWith("${this@MasterStats::class.qualifiedName}")) {
                interaction.deferEphemeralResponse().respond(createGenericEmbedError(CommonLocale.INTERACTION_STARTED_BY_OTHER.locale(locale)))
            }
        }
    }

    private fun generateSessionEmbed(sessions: PaginatedList<PlayerMasteredSessions>, user: User, locale: String): InteractionResponseModifyBuilder.() -> Unit =
        fun InteractionResponseModifyBuilder.() {
            embed {
                title = "${MasterStarsLocale.SESSION_MASTERED_TITLE.locale(locale)}${user.username} ${sessions.size}"
                sessions.aggregateByCharacter().forEach {
                    field {
                        name = it.key
                        value = it.value.toString()
                        inline = true
                    }
                }
                field {
                    name = MasterStarsLocale.SESSION_LIST.locale(locale)
                    value = MasterStarsLocale.NAVIGATE.locale(locale)
                    inline = false
                }
                sessions.page.forEach {
                    field {
                        name = MasterStarsLocale.TITLE.locale(locale)
                        value = it.title
                        inline = true
                    }
                    field {
                        name = MasterStarsLocale.DATE.locale(locale)
                        value = SimpleDateFormat("dd/MM/yyyy").format(it.date)
                        inline = true
                    }
                    field {
                        name = "master"
                        value = it.master
                        inline = true
                    }
                }
            }
            actionRow {
                interactionButton(ButtonStyle.Secondary, "${this@MasterStats::class.qualifiedName}-previous") {
                    label = MasterStarsLocale.LABEL_PREVIOUS.locale(locale)
                }
                interactionButton(ButtonStyle.Secondary, "${this@MasterStats::class.qualifiedName}-next") {
                    label = MasterStarsLocale.LABEL_NEXT.locale(locale)
                }
                interactionButton(ButtonStyle.Primary, "${this@MasterStats::class.qualifiedName}-export") {
                    label = MasterStarsLocale.LABEL_EXPORT.locale(locale)
                }
            }
        }


    override suspend fun handle(command: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val locale = command.interaction.locale?.language ?: command.interaction.guildLocale?.language ?: "en"
        val guildId = command.interaction.guildId
        val user = command.interaction.command.users["master"] ?: command.interaction.user
        val sessions = PaginatedList(db.sessionScope.getAllMasteredSessions(guildId.toString(), user.id.toString()).toList().sortedBy { it.date }.reversed())
        return if (!sessions.isEmpty()) {
            generateSessionEmbed(sessions, user, locale)
        } else {
            fun InteractionResponseModifyBuilder.() {
                embed {
                    title = "${MasterStarsLocale.NEVER_MASTERED_TITLE.locale(locale)}${user.username}"
                    description = MasterStarsLocale.NEVER_MASTERED_DESCRIPTION.locale(locale)
                }
            }
        }
    }

    override suspend fun handleResponse(
        msg: PublicMessageInteractionResponse,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
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

}

fun PaginatedList<PlayerMasteredSessions>.aggregateByCharacter(): Map<String, Int> =
    this.elements.fold(mapOf()) { acc, it ->
        if(acc.containsKey(it.master)) {
            acc + (it.master to (acc[it.master]!! + 1))
        } else {
            acc + (it.master to 1)
        }
    }

fun PaginatedList<PlayerMasteredSessions>.toCSVFile(): String =
    "TITLE\tDATE\tGAME_DATE\tMASTER\n" +
            this.elements.joinToString(separator = "\n") {
                "${it.title}\t" +
                    "${SimpleDateFormat("dd/MM/yyyy").format(it.date)}\t" +
                    "${it.gameDate.day}-${it.gameDate.month}-${it.gameDate.year},${it.gameDate.season}\t" +
                    it.master
            }