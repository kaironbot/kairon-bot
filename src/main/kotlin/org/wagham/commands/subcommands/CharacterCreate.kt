package org.wagham.commands.subcommands

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.kord.common.Locale
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.TextInputStyle
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.ComponentInteractionBehavior
import dev.kord.core.behavior.interaction.followup.edit
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.response.createPublicFollowup
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.User
import dev.kord.core.entity.component.TextInputComponent
import dev.kord.core.entity.interaction.ModalSubmitInteraction
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.ModalSubmitInteractionCreateEvent
import dev.kord.core.event.interaction.SelectMenuInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.component.option
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.FollowupMessageModifyBuilder
import dev.kord.rest.builder.message.modify.actionRow
import dev.kord.rest.builder.message.modify.embed
import org.wagham.annotations.BotSubcommand
import org.wagham.commands.SubCommand
import org.wagham.commands.impl.CharacterCommand
import org.wagham.components.CacheManager
import org.wagham.config.Colors
import org.wagham.config.locale.CommonLocale
import org.wagham.config.locale.subcommands.CharacterCreateLocale
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.ServerConfig.Companion.PlayerConfigurations.CHARACTER_CREATION_STRICT_CHECK
import org.wagham.db.models.creation.CharacterCreationData
import org.wagham.exceptions.ModalValueError
import org.wagham.utils.defaultLocale
import org.wagham.utils.extractCommonParameters
import org.wagham.utils.replyOnError
import java.util.concurrent.TimeUnit

@BotSubcommand("all", CharacterCommand::class)
class CharacterCreate(
    override val kord: Kord,
    override val db: KabotMultiDBClient,
    override val cacheManager: CacheManager
) : SubCommand<ModalBuilder> {

    override val commandName = "create"
    override val defaultDescription = CharacterCreateLocale.DESCRIPTION.locale(defaultLocale)
    override val localeDescriptions: Map<Locale, String> = CharacterCreateLocale.DESCRIPTION.localeMap
    private val interactionCache: Cache<Snowflake, PartialCharacterData> =
        Caffeine.newBuilder()
            .expireAfterWrite(20, TimeUnit.MINUTES)
            .build()
    companion object {
        const val CREATE_MODAL = "createModal"
        const val NAME_INPUT = "name"
        const val STARTING_LEVEL = "startingLevel"
        const val STARTING_CLASS = "startingClass"
        const val RACE = "race"
        const val ORIGIN = "territory"
        const val AGE = "age"
        const val ABORT = "abort"
        const val CONFIRM = "confirm"
        const val OPTION = "option"

        private data class PartialCharacterData (
            val responsible: Snowflake,
            val target: User,
            val name: String? = null,
            val characterClass: String? = null,
            val startingLevel: String? = null,
            val race: String? = null,
            val origin: String = "",
            val age: Int = 0,
            val followUpMessage: Snowflake? = null
        ) {

            fun toCreationData(locale: String) = CharacterCreationData(
                name = name ?: throw ModalValueError(CharacterCreateLocale.CHARACTER_NAME_INVALID.locale(locale)),
                startingLevel = startingLevel ?: throw ModalValueError(CharacterCreateLocale.CHARACTER_LEVEL_INVALID.locale(locale)),
                race = race ?: throw ModalValueError(CharacterCreateLocale.MISSING_RACE.locale(locale)),
                characterClass = characterClass ?: throw ModalValueError(CharacterCreateLocale.MISSING_CLASS.locale(locale)),
                territory = origin,
                age = age
            )

            fun buildDescription(locale: String) = buildString {
                append(CharacterCreateLocale.CHARACTER_NAME.locale(locale))
                append(": ")
                append("$name\n")
                append(CharacterCreateLocale.CHARACTER_LEVEL.locale(locale))
                append(": ")
                append("$startingLevel\n")
                characterClass?.also {
                    append(CharacterCreateLocale.CHARACTER_CLASS.locale(locale))
                    append(": $it\n")
                }
                race?.also {
                    append(CharacterCreateLocale.CHARACTER_RACE.locale(locale))
                    append(": $it\n")
                }
                origin.takeIf { it.isNotBlank() }?.also {
                    append(CharacterCreateLocale.ORIGIN.locale(locale))
                    append(": $it\n")
                }
                age.takeIf { it > 0 }?.also {
                    append(CharacterCreateLocale.AGE.locale(locale))
                    append(": $it\n")
                }
            }

            fun isInvalid() = name == null || startingLevel == null
        }

        private enum class MissingOptions{ RACE, CLASS }
    }

    override fun create(ctx: RootInputChatBuilder) = ctx.subCommand(commandName, defaultDescription) {
        localeDescriptions.forEach{ (locale, description) ->
            description(locale, description)
        }
        user("user", CharacterCreateLocale.PLAYER.locale("en")) {
            CharacterCreateLocale.PLAYER.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            autocomplete = true
            required = true
        }
        string(NAME_INPUT, CharacterCreateLocale.CHARACTER_NAME.locale("en")) {
            CharacterCreateLocale.CHARACTER_NAME.localeMap.forEach{ (locale, description) ->
                description(locale, description)
            }
            required = true
        }
    }

    private suspend fun handleModal() = kord.on<ModalSubmitInteractionCreateEvent> {
        if (verifyId(interaction.modalId, CREATE_MODAL)) {
            replyOnError(interaction) {
                val locale = it.locale?.language ?: it.guildLocale?.language ?: "en"
                val guildId = it.data.guildId.value ?: throw IllegalStateException("GuildId not found")
                val targetId = Snowflake(it.modalId.split("-").last())
                val currentData = interactionCache.getIfPresent(targetId)
                when {
                    currentData == null ->
                        throw IllegalStateException(CharacterCreateLocale.PROCESS_EXPIRED.locale(locale))
                    currentData.responsible != it.user.id ->
                        throw IllegalStateException(CommonLocale.INTERACTION_STARTED_BY_OTHER.locale(locale))
                    else -> {
                        val modalData = it.extractDataFromModal(guildId, locale, currentData).also { data ->
                            interactionCache.put(targetId, data)
                        }
                        createOrAskForCompleteData(
                            it,
                            modalData,
                            locale,
                            guildId)
                    }
                }
            }
        }
    }

    private suspend fun handleSelection() = kord.on<SelectMenuInteractionCreateEvent> {
        if(verifyId(interaction.componentId, OPTION)) {
            val (_, _, _, type, user) = interaction.componentId.split("-")
            val optionType = MissingOptions.valueOf(type)
            val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
            val guildId = interaction.data.guildId.value ?: throw IllegalStateException("GuildId not found")
            val currentData = interactionCache.getIfPresent(Snowflake(user))
            when {
                currentData == null ->
                    throw IllegalStateException(CharacterCreateLocale.PROCESS_EXPIRED.locale(locale))
                currentData.responsible != interaction.user.id ->
                    throw IllegalStateException(CommonLocale.INTERACTION_STARTED_BY_OTHER.locale(locale))
                optionType == MissingOptions.CLASS -> {
                    interactionCache.put(Snowflake(user), currentData.copy(characterClass = interaction.values.first()))
                }
                optionType == MissingOptions.RACE -> {
                    interactionCache.put(Snowflake(user), currentData.copy(race = interaction.values.first()))
                }
            }
            val data = interactionCache.getIfPresent(Snowflake(user))
            val options = db.utilityScope.getPlayableResources(guildId.toString())
            if(data?.followUpMessage != null) {
                interaction.deferPublicMessageUpdate().getFollowupMessage(data.followUpMessage).edit(
                    createSelectionMessage(
                        optionType,
                        data,
                        locale,
                        when(optionType) {
                            MissingOptions.CLASS -> options.classes
                            MissingOptions.RACE -> options.races
                        }
                    )
                )
            }
        }
    }

    private suspend fun handleSelectionConfirmation() = kord.on<ButtonInteractionCreateEvent> {
        if(verifyId(interaction.componentId, CONFIRM) || verifyId(interaction.componentId, ABORT)) {
            val (_, op, _, user) = interaction.componentId.split("-")
            val locale = interaction.locale?.language ?: interaction.guildLocale?.language ?: "en"
            val guildId = interaction.data.guildId.value ?: throw IllegalStateException("GuildId not found")
            val currentData = interactionCache.getIfPresent(Snowflake(user))
            when {
                currentData == null ->
                    throw IllegalStateException(CharacterCreateLocale.PROCESS_EXPIRED.locale(locale))
                currentData.responsible != interaction.user.id ->
                    throw IllegalStateException(CommonLocale.INTERACTION_STARTED_BY_OTHER.locale(locale))
                op == ABORT -> {
                    interactionCache.invalidate(Snowflake(user))
                    interaction.deferPublicMessageUpdate().edit {
                        embed {
                            color = Colors.WARNING.value
                            title = CharacterCreateLocale.ABORTED.locale(locale)
                        }
                        components = mutableListOf()
                    }
                }
                op == CONFIRM -> {
                    createOrAskForCompleteData(interaction, currentData, locale, guildId)
                }
            }
        }
    }

    override suspend fun registerCommand() {
        handleModal()
        handleSelection()
        handleSelectionConfirmation()
    }

    override suspend fun execute(event: GuildChatInputCommandInteractionCreateEvent): ModalBuilder.() -> Unit {
        val params = event.extractCommonParameters()
        val userId = event.interaction.user.id
        val targetUser = event.interaction.command.users["user"]
            ?: throw IllegalStateException("Target user not found")
        val characterName = event.interaction.command.strings[NAME_INPUT]?.trim()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(CharacterCreateLocale.CHARACTER_NAME_INVALID.locale(params.locale))
        val expTable = cacheManager.getExpTable(params.guildId)
        val config = cacheManager.getConfig(params.guildId)
        val characterOptions = db.utilityScope.getPlayableResources(params.guildId.toString())
        interactionCache.put(
            targetUser.id,
            PartialCharacterData(userId, targetUser, characterName)
        )
        return fun ModalBuilder.() {
            title = buildString {
                append(CharacterCreateLocale.MODAL_TITLE.locale(params.locale))
                append(": $characterName")
            }.let {
                if(it.length <= 45) it
                else "${it.substring(0, 42)}..."
            }
            customId = buildElementId(CREATE_MODAL, targetUser.id.toString())
            actionRow {
                textInput(
                    TextInputStyle.Short, STARTING_LEVEL, CharacterCreateLocale.CHARACTER_LEVEL.locale(params.locale)
                ) {
                    value = expTable.table.entries.toList().minByOrNull { it.key }?.value
                    allowedLength = 1 .. 10
                    required = true
                }
            }
            if (!config.playerConfigurations.getOrDefault(CHARACTER_CREATION_STRICT_CHECK, false) || characterOptions.classes.isEmpty()) {
                actionRow {
                    textInput(
                        TextInputStyle.Short, STARTING_CLASS, CharacterCreateLocale.CHARACTER_CLASS.locale(params.locale)
                    ) {
                        allowedLength = 1 .. 20
                        required = true
                    }
                }
            }
            if (!config.playerConfigurations.getOrDefault(CHARACTER_CREATION_STRICT_CHECK, false) || characterOptions.races.isEmpty()) {
                actionRow {
                    textInput(
                        TextInputStyle.Short, RACE, CharacterCreateLocale.CHARACTER_RACE.locale(params.locale)
                    ) {
                        allowedLength = 1 .. 50
                        required = true
                    }
                }
            }
            actionRow {
                textInput(
                    TextInputStyle.Short, ORIGIN, CharacterCreateLocale.ORIGIN.locale(params.locale)
                ) {
                    allowedLength = 1 .. 100
                    required = false
                }
            }
            actionRow {
                textInput(
                    TextInputStyle.Short, AGE, CharacterCreateLocale.AGE.locale(params.locale)
                ) {
                    allowedLength = 1 .. 10
                    required = false
                }
            }
        }
    }

    override suspend fun handleResponse(
        builder: ModalBuilder.() -> Unit,
        event: GuildChatInputCommandInteractionCreateEvent
    ) {
        event.interaction.modal("title", "id", builder)
    }

    private suspend fun createOrAskForCompleteData(
        interaction: ComponentInteractionBehavior,
        partialData: PartialCharacterData,
        locale: String,
        guildId: Snowflake,
    ) {
        val updateBehavior = interaction.deferPublicMessageUpdate()
        val data = partialData.takeIf { it.followUpMessage != null } ?: run {
            val msg = updateBehavior.createPublicFollowup { embed {
                color = Colors.DEFAULT.value
                title = "Loading..."
            } }
            val updatedData = partialData.copy(followUpMessage = msg.id)
            interactionCache.put(updatedData.target.id, updatedData)
            updatedData
        }

        when {
            data.isInvalid() || data.followUpMessage == null -> throw IllegalStateException(CharacterCreateLocale.INVALID_CHARACTER_DATA.locale(locale))
            data.characterClass == null -> {
                val config = cacheManager.getConfig(guildId)
                val options = db.utilityScope.getPlayableResources(guildId.toString())
                if (config.playerConfigurations.getOrDefault(CHARACTER_CREATION_STRICT_CHECK, false) && options.classes.isNotEmpty()) {
                    updateBehavior.getFollowupMessage(data.followUpMessage).edit(
                        createSelectionMessage(MissingOptions.CLASS, data, locale, options.classes)
                    )
                } else throw IllegalStateException(CharacterCreateLocale.INVALID_CHARACTER_DATA.locale(locale))
            }
            data.race == null -> {
                val config = cacheManager.getConfig(guildId)
                val options = db.utilityScope.getPlayableResources(guildId.toString())
                if (config.playerConfigurations.getOrDefault(CHARACTER_CREATION_STRICT_CHECK, false) && options.races.isNotEmpty()) {
                    updateBehavior.getFollowupMessage(data.followUpMessage).edit(
                        createSelectionMessage(MissingOptions.RACE, data, locale, options.races)
                    )
                } else throw IllegalStateException(CharacterCreateLocale.INVALID_CHARACTER_DATA.locale(locale))
            }
            else -> db.charactersScope.createCharacter(
                    guildId.toString(),
                    data.target.id.toString(),
                    data.target.username,
                    data.toCreationData(locale)
                ).let {
                    if(it.committed) {
                        updateBehavior.getFollowupMessage(data.followUpMessage).edit {
                            embed {
                                color = Colors.DEFAULT.value
                                title = "Ok"
                                description = data.buildDescription(locale)
                            }
                            components = mutableListOf()
                        }
                    } else {
                        updateBehavior.getFollowupMessage(data.followUpMessage).edit {
                            embed {
                                color = Colors.WARNING.value
                                title = "Error"
                                description = it.exception?.message ?: it.exception?.stackTraceToString()?.substring(0, 1000)
                            }
                            components = mutableListOf()
                        }
                    }
                }
        }
    }

    private fun  createSelectionMessage(type: MissingOptions, data: PartialCharacterData, locale: String, options: List<String>) = fun FollowupMessageModifyBuilder.() {
        embed {
            title = buildString {
                append(CharacterCreateLocale.CHARACTER_FOR.locale(locale))
                append(" ")
                append(data.target.username)
            }
            description = buildString {
                append(data.buildDescription(locale))
                append("\n**")
                append(CharacterCreateLocale.INSERT_MISSING.locale(locale))
                append(" ${type.name.lowercase()}**")
            }
            color = Colors.DEFAULT.value
        }
        options.chunked(24).forEachIndexed { index, chunk ->
            actionRow {
                stringSelect(buildElementId( OPTION, index, type.name, data.target.id)) {
                    chunk.forEach {
                        option(it, it) {
                            default = data.characterClass == it || data.race == it
                        }
                    }
                }
            }
        }
        actionRow {
            interactionButton(ButtonStyle.Primary, buildElementId(CONFIRM, type.name, data.target.id)) {
                label = CommonLocale.CONTINUE.locale(locale)
            }
            interactionButton(ButtonStyle.Danger, buildElementId(ABORT, type.name, data.target.id)) {
                label = CommonLocale.ABORT.locale(locale)
            }
        }
    }

    private suspend fun ModalSubmitInteraction.extractDataFromModal(guildId: Snowflake, locale: String, currentData: PartialCharacterData): PartialCharacterData {
        val actionRows = actionRows.fold(emptyMap<String, TextInputComponent>()) { acc, it ->
            acc + it.textInputs
        }
        val expTable = cacheManager.getExpTable(guildId)
        val startingLevel = actionRows[STARTING_LEVEL]?.value?.trim()?.takeIf { it.isNotBlank() }?.also {
            try {
                expTable.levelToExp(it)
            } catch(_: Exception) {
                throw ModalValueError(CharacterCreateLocale.CHARACTER_LEVEL_INVALID.locale(locale))
            }
        } ?: throw ModalValueError(CharacterCreateLocale.CHARACTER_LEVEL_INVALID.locale(locale))
        val characterClass = actionRows[STARTING_CLASS]?.value?.trim()?.takeIf { it.isNotBlank() }
        val race = actionRows[RACE]?.value?.trim()?.takeIf { it.isNotBlank() }
        val origin = actionRows[ORIGIN]?.value?.trim()?.takeIf { it.isNotBlank() } ?: ""
        val age = actionRows[AGE]?.value?.toIntOrNull() ?: 0
        return PartialCharacterData(
            currentData.responsible,
            currentData.target,
            currentData.name,
            characterClass,
            startingLevel,
            race,
            origin,
            age)
    }
}