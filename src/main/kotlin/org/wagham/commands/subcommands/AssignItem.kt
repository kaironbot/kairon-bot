package org.wagham.commands.subcommands

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.builder.message.modify.InteractionResponseModifyBuilder
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.AssignCommand
import org.wagham.components.CacheManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.AssignItemLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.TransactionType
import org.wagham.db.exceptions.NoActiveCharacterException
import org.wagham.db.models.Item
import org.wagham.db.models.embed.Transaction
import org.wagham.exceptions.GuildNotFoundException
import org.wagham.utils.*
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.TimeUnit

@BotSubcommand("all", AssignCommand::class)
class AssignItem(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder> {

    override val commandName = "item"
    override val defaultDescription = "Assign an item to one or more players"
    override val localeDescriptions: Map<Locale, String> = mapOf(
        Locale.ENGLISH_GREAT_BRITAIN to "Assign an item to one or more players",
        Locale.ITALIAN to "Assegna delle monete a uno o più giocatori"
    )
    private val additionalUsers: Int = 5
    private val interactionCache: Cache<Snowflake, Pair<Snowflake, Set<Snowflake>>> =
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
            val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
            if(interaction.componentId.startsWith("${this@AssignItem::class.qualifiedName}") && interactionCache.getIfPresent(interaction.message.id)?.first == interaction.user.id) {
                val guildId = interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
                val targets = interactionCache.getIfPresent(interaction.message.id)?.second ?: throw IllegalStateException("Cannot find targets")
                val (item, amount) = Regex("${this@AssignItem::class.qualifiedName}-(.+)-([0-9]+)")
                    .find(interaction.componentId)
                    ?.groupValues
                    ?.let {
                        Pair(it[1], it[2].toInt())
                    } ?: throw IllegalStateException("Cannot parse parameters")
                assignItemToCharacters(guildId, item, amount, targets).let {
                    when {
                        it.committed -> createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
                        it.exception is NoActiveCharacterException -> createGenericEmbedError(CommonLocale.NO_ACTIVE_CHARACTER.locale(locale))
                        else -> createGenericEmbedError("Error: ${it.exception?.stackTraceToString()}")
                    }
                }.let {
                    interaction.deferPublicMessageUpdate().edit(it)
                }
            } else if (interaction.componentId.startsWith("${this@AssignItem::class.qualifiedName}") && interactionCache.getIfPresent(interaction.message.id) == null) {
                interaction.deferEphemeralMessageUpdate().edit(createGenericEmbedError(CommonLocale.INTERACTION_EXPIRED.locale(locale)))
            } else if (interaction.componentId.startsWith("${this@AssignItem::class.qualifiedName}")) {
                interaction.deferEphemeralResponse().respond(createGenericEmbedError(CommonLocale.INTERACTION_STARTED_BY_OTHER.locale(locale)))
            }
        }
    }

    private suspend fun assignItemToCharacters(guildId: String, item: String, amount: Int, targets: Set<Snowflake>) =
        db.transaction(guildId) { s ->
            targets.fold(true) { acc, it ->
                val targetCharacter = db.charactersScope.getActiveCharacter(guildId, it.toString())
                acc && db.charactersScope.addItemToInventory(s, guildId, targetCharacter.id, item, amount) &&
                    db.characterTransactionsScope.addTransactionForCharacter(
                        s, guildId, targetCharacter.id, Transaction(
                            Date(), null, "ASSIGN", TransactionType.ADD, mapOf(item to amount.toFloat())
                        ))
            }
        }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit {
        val guildId = event.interaction.data.guildId.value?.toString() ?: throw GuildNotFoundException()
        val locale = event.interaction.locale?.language ?: event.interaction.guildLocale?.language ?: "en"
        val items = cacheManager.getCollectionOfType<Item>(guildId)
        val targets = listOf(
            listOfNotNull(event.interaction.command.users["target"]?.id),
            (1 .. additionalUsers).mapNotNull { paramNum ->
                event.interaction.command.users["target-$paramNum"]?.id
            }
        ).flatten().toSet()
        val amount = event.interaction.command.integers["amount"]?.toInt()?.takeIf { it > 0 }
            ?: throw IllegalStateException("Invalid amount")
        val item = event.interaction.command.strings["item"] ?: throw IllegalStateException("Item not found")
        return try {
            if (items.firstOrNull { it.name == item } == null) {
                val probableItem = items.maxByOrNull { item.levenshteinDistance(it.name) }
                alternativeOptionMessage(locale, item, probableItem?.name, "${this@AssignItem::class.qualifiedName}-${probableItem?.name}-$amount")
            } else {
                assignItemToCharacters(guildId, item, amount, targets).let {
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
                listOf(
                    listOfNotNull(event.interaction.command.users["target"]?.id),
                    (1 .. additionalUsers).mapNotNull { paramNum ->
                        event.interaction.command.users["target-$paramNum"]?.id
                    }
                ).flatten().toSet()
            )
        )
    }

}