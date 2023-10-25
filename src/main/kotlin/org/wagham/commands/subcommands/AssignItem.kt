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
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.AssignCommand
import org.wagham.components.CacheManager
import org.wagham.components.MultiCharacterCommand
import org.wagham.components.MultiCharacterManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.AssignItemLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.TransactionType
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Character
import org.wagham.db.models.Item
import org.wagham.db.models.embed.Transaction
import org.wagham.entities.InteractionParameters
import org.wagham.utils.*
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.IllegalStateException

@BotSubcommand("all", AssignCommand::class)
class AssignItem(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder>, MultiCharacterCommand<Pair<String, Int>> {

    companion object {
        private data class AssignItemInteractionParameters(
            val responsible: Snowflake,
            val users: Set<User>,
            val item: String,
            val amount: Int
        )
    }

    override val commandName = "item"
    override val defaultDescription = AssignItemLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = AssignItemLocale.DESCRIPTION.localeMap
    private val multiCharacterManager = MultiCharacterManager(db, kord, this)
    private val additionalUsers: Int = 5
    private val interactionCache: Cache<String, AssignItemInteractionParameters> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build()

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        string("item", AssignItemLocale.ITEM.locale("en")) {
            AssignItemLocale.ITEM.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
        integer("amount", AssignItemLocale.AMOUNT.locale("en")) {
            AssignItemLocale.AMOUNT.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
        user("target", AssignItemLocale.TARGET.locale("en")) {
            AssignItemLocale.TARGET.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
            autocomplete = true
        }
        (1 .. additionalUsers).forEach { paramIndex ->
            user("target-$paramIndex", AssignItemLocale.ANOTHER_TARGET.locale("en")) {
                AssignItemLocale.ANOTHER_TARGET.localeMap.forEach{ (locale, description) ->
                    description(locale, description)
                }
                required = false
                autocomplete = true
            }
        }
    }

    override suspend fun registerCommand() {
        kord.on<ButtonInteractionCreateEvent> {
            if (verifyId(interaction.componentId)) {
                val (id) = extractComponentsFromComponentId(interaction.componentId)
                val data = interactionCache.getIfPresent(id)
                val params = interaction.extractCommonParameters()
                when {
                    data == null -> interaction.updateWithExpirationError(params.locale)
                    data.responsible != params.responsible.id -> interaction.respondWithForbiddenError(params.locale)
                    else -> {
                        try {
                            val targetsOrSelectionContext = multiCharacterManager.startSelectionOrReturnCharacters(
                                data.users.toList(),
                                null,
                                Pair(data.item, data.amount),
                                params
                            )
                            when {
                                targetsOrSelectionContext.characters != null -> assignItemToCharacters(data.item, data.amount, targetsOrSelectionContext.characters, params)
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

    private suspend fun assignItemToCharacters(item: String, amount: Int, targets: Collection<Character>, params: InteractionParameters) =
        db.transaction(params.guildId.toString()) { s ->
            targets.fold(true) { acc, targetCharacter ->
                acc && db.charactersScope.addItemToInventory(s, params.guildId.toString(), targetCharacter.id, item, amount) &&
                    db.characterTransactionsScope.addTransactionForCharacter(
                        s, params.guildId.toString(), targetCharacter.id, Transaction(
                            Date(), null, "ASSIGN", TransactionType.ADD, mapOf(item to amount.toFloat())
                        ))
            }
        }.let {
            when {
                it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(params.locale))
                it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(params.locale))
                else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
            }
        }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        val items = cacheManager.getCollectionOfType<Item>(guildId)
        val targets = listOf(
            listOfNotNull(event.interaction.command.users["target"]),
            (1 .. additionalUsers).mapNotNull { paramNum ->
                event.interaction.command.users["target-$paramNum"]
            }
        ).flatten().toSet()
        val amount = event.interaction.command.integers["amount"]?.toInt()?.takeIf { it > 0 }
            ?: throw IllegalStateException("Invalid amount")
        val item = event.interaction.command.strings["item"] ?: throw IllegalStateException("Item not found")
        return items.firstOrNull { it.name == item }?.let {
            val targetsOrSelectionContext = multiCharacterManager.startSelectionOrReturnCharacters(targets.toList(), null, Pair(it.name, amount), this)
            when {
                targetsOrSelectionContext.characters != null -> assignItemToCharacters(it.name, amount, targetsOrSelectionContext.characters, this)
                targetsOrSelectionContext.response != null -> targetsOrSelectionContext.response
                else -> createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))
            }
        } ?: items.maxByOrNull { item.levenshteinDistance(it.name) }?.let {
            val interactionId = compactUuid()
            interactionCache.put(
                interactionId,
                AssignItemInteractionParameters(
                    responsible.id,
                    targets,
                    item,
                    amount
                )
            )
            alternativeOptionMessage(locale, item, it.name, buildElementId(interactionId))
        } ?: createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))

    }

    override suspend fun multiCharacterAction(
        interaction: ComponentInteraction,
        characters: List<Character>,
        context: Pair<String, Int>,
        sourceCharacter: Character?
    ) {
        val params = interaction.extractCommonParameters()
        val (item, amount) = context
        interaction.deferPublicMessageUpdate().edit(
            assignItemToCharacters(item, amount, characters, params)
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