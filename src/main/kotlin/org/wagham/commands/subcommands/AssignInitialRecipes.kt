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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.AssignCommand
import org.wagham.components.CacheManager
import org.wagham.components.MultiCharacterCommand
import org.wagham.components.MultiCharacterManager
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.AssignInitialRecipesLocale
import org.wagham.config.locale.subcommands.AssignItemLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.enums.TransactionType
import org.wagham.db.models.Character
import org.wagham.db.models.Item
import org.wagham.db.models.embed.LabelStub
import org.wagham.db.models.embed.Transaction
import org.wagham.entities.InteractionParameters
import org.wagham.utils.createGenericEmbedError
import org.wagham.utils.createGenericEmbedSuccess
import org.wagham.utils.defaultLocale
import org.wagham.utils.extractCommonParameters
import org.wagham.utils.withEventParameters
import java.util.*

@BotSubcommand("wagham", AssignCommand::class)
class AssignInitialRecipes(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<InteractionResponseModifyBuilder>, MultiCharacterCommand<Unit> {

    override val commandName = "initial_recipes"
    override val defaultDescription = AssignInitialRecipesLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = AssignItemLocale.DESCRIPTION.localeMap
    private val multiCharacterManager = MultiCharacterManager(db, kord, this)

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        user("target", AssignInitialRecipesLocale.TARGET.locale("en")) {
            AssignInitialRecipesLocale.TARGET.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
            autocomplete = true
        }
    }

    override suspend fun registerCommand() {}

    private suspend fun getRandomItem(guildId: String, tier: Int, character: Character): Item {
        val labels = db.labelsScope.getLabelsByName(guildId, listOf("T$tier") + character.characterClass).map {
            it.toLabelStub()
        }.toList() + LabelStub("8c7f4255-f694-4bc8-ae2b-fb95bbd5bc3f", "Recipe")
        return db.itemsScope.getItems(guildId, labels).toList().random()
    }

    private suspend fun InteractionParameters.assignRecipesToCharacter(character: Character): InteractionResponseModifyBuilder.() -> Unit {
        val expTable = cacheManager.getExpTable(guildId)
        val level = expTable.expToLevel(character.ms().toFloat()).toInt()
        val recipes = (3 .. level).filter { it % 2 == 1 }.map {
            val levelExp = expTable.levelToExp("$it")
            val tier = expTable.expToTier(levelExp.toFloat()).toInt().coerceAtMost(4)
            getRandomItem(guildId.toString(), tier, character)
        }
        return db.transaction(guildId.toString()) {
            recipes.all { recipe ->
                db.charactersScope.addItemToInventory(it, guildId.toString(), character.id, recipe.name, 1)
            } && db.characterTransactionsScope.addTransactionForCharacter(
                it, guildId.toString(), character.id, Transaction(
            Date(), null, "INITIAL_RECIPES", TransactionType.ADD, recipes.map { r -> r.name }.associateWith { 1f }
            ))
        }.let {
            if(it.committed) createGenericEmbedSuccess(CommonLocale.SUCCESS.locale(locale))
            else createGenericEmbedError(locale)
        }

    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): InteractionResponseModifyBuilder.() -> Unit = withEventParameters(event) {
        val target = event.interaction.command.users["target"]
            ?: throw IllegalStateException("User not found")
        val targetsOrSelectionContext = multiCharacterManager.startSelectionOrReturnCharacters(
            listOf(target), null, Unit, this)
        when {
            targetsOrSelectionContext.characters != null -> assignRecipesToCharacter(targetsOrSelectionContext.characters.first())
            targetsOrSelectionContext.response != null -> targetsOrSelectionContext.response
            else -> createGenericEmbedError(CommonLocale.GENERIC_ERROR.locale(locale))
        }
    }

    override suspend fun multiCharacterAction(
        interaction: ComponentInteraction,
        characters: List<Character>,
        context: Unit,
        sourceCharacter: Character?
    ) {
        val params = interaction.extractCommonParameters()
        interaction.deferPublicMessageUpdate().edit(
            params.assignRecipesToCharacter(characters.first())
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