package org.wagham.commands.subcommands

import dev.kord.common.Locale
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ComponentInteraction
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.RootInputChatBuilder
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.interaction.user
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.StatsCommand
import org.wagham.components.CacheManager
import org.wagham.components.MultiCharacterCommand
import org.wagham.components.MultiCharacterManager
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.StatsTransactionLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.Character
import org.wagham.entities.InteractionParameters
import org.wagham.utils.*
import java.text.SimpleDateFormat

@BotSubcommand("all", StatsCommand::class)
class StatsTransactions(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder>, MultiCharacterCommand<Unit> {

    override val commandName = "transactions"
    override val defaultDescription = StatsTransactionLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = StatsTransactionLocale.DESCRIPTION.localeMap
    private val multiCharacterManager = MultiCharacterManager(db, kord, this)
    private val lastTransactions = 20
    private val dateFormatter = SimpleDateFormat("dd/MM/yy HH:mm:ss")

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        user("target", StatsTransactionLocale.TARGET.locale("en")) {
            StatsTransactionLocale.TARGET.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = false
            autocomplete = true
        }
    }

    override suspend fun registerCommand() { }

    override suspend fun multiCharacterAction(
        interaction: ComponentInteraction,
        characters: List<Character>,
        context: Unit,
        sourceCharacter: Character?
    ) {
        interaction.deferPublicMessageUpdate().edit(
            generateTransactionEmbed(characters.first(), interaction.extractCommonParameters())
        )
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        event.interaction.command.users["target"]?.takeIf { it.id != responsible.id }?.let { target ->
            val targetOrSelectionContext = multiCharacterManager.startSelectionOrReturnCharacters(listOf(target), null, Unit, this)
            when {
                targetOrSelectionContext.characters != null -> generateTransactionEmbed(targetOrSelectionContext.characters.first(), this)
                targetOrSelectionContext.response != null -> targetOrSelectionContext.response
                else -> createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))
            }
        } ?: withOneActiveCharacterOrErrorMessage(responsible, this) { character ->
            generateTransactionEmbed(character, this)
        }
    }

    override suspend fun handleResponse(
        builder: InteractionResponseModifyBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        val response = event.interaction.deferPublicResponse()
        response.respond(builder)
    }

    private suspend fun generateTransactionEmbed(character: Character, params: InteractionParameters) = with(params) {
        val transactions = db.characterTransactionsScope.getLastTransactions(guildId.toString(), character.id, lastTransactions)
            .takeIf { it.isNotEmpty() }
        fun InteractionResponseModifyBuilder.() {
            embed {
                title = "${StatsTransactionLocale.TITLE.locale(locale)} ${character.name}"
                color = Colors.DEFAULT.value

                description = transactions?.let { transactions ->
                    buildString {
                        transactions.forEach { t ->
                            append("${dateFormatter.format(t.date)} ")
                            append("${t.operation} ")
                            append("${t.type} ")
                            append(t.args.entries.joinToString(", ") { "${it.key} x${it.value}" })
                            append("\n")
                        }
                    }
                } ?: StatsTransactionLocale.NO_TRANSACTIONS.locale(locale)
            }
        }
    }

}