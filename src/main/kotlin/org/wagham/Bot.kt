package org.wagham

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.RequestBuilder
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.reflections.Reflections
import org.wagham.annotations.BotCommand
import org.wagham.annotations.BotEvent
import org.wagham.commands.Command
import org.wagham.components.CacheManager
import org.wagham.components.ExternalGateway
import org.wagham.components.SchedulingManager
import org.wagham.config.Channels
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.*
import org.wagham.db.pipelines.buildings.BuildingWithBounty
import org.wagham.events.Event
import kotlin.reflect.full.primaryConstructor

class KaironBot(
    private val profile: String,
    private val kord: Kord
) {

    private val database = KabotMultiDBClient(
        MongoCredentials(
            "ADMIN",
            System.getenv("DB_ADMIN_USER"),
            System.getenv("DB_ADMIN_PWD"),
            System.getenv("DB_ADMIN_NAME"),
            System.getenv("DB_ADMIN_IP"),
            System.getenv("DB_ADMIN_PORT").toInt()
        )
    )
    private val schedulingManager = SchedulingManager(database)
    private val cacheManager = CacheManager(database, schedulingManager, profile)
    private val logger = KotlinLogging.logger {}
    private val commands: List<Command<RequestBuilder<*>>>
    private val events: List<Event>

    private fun autowireCommands() = Reflections("org.wagham.commands")
        .getTypesAnnotatedWith(BotCommand::class.java)
        .map { it.kotlin }
        .filter {
            it.annotations.any { ann ->
                ann is BotCommand && (ann.profile == "all" || ann.profile == profile)
            }
        }
        .map {
            it.primaryConstructor!!.call(kord, database, cacheManager) as Command<RequestBuilder<*>>
        }

    private fun autowireEvents() = Reflections("org.wagham.events")
        .getTypesAnnotatedWith(BotEvent::class.java)
        .map { it.kotlin }
        .filter {
            it.annotations.any { ann ->
                ann is BotEvent && (ann.profile == "all" || ann.profile == profile)
            }
        }
        .map {
            it.primaryConstructor!!.call(kord, database, cacheManager) as Event
        }

    init {
        commands = autowireCommands().map {
            cacheManager.registerCommand(it.commandName)
            it
        }
        events = autowireEvents()

        kord.on<ReadyEvent> {
            kord.getGlobalApplicationCommands(true).collect {
                if(!cacheManager.getCommands().contains(it.name)) {
                    it.delete()
                    logger.info { "Deleting ${it.name} command" }
                }
            }
            supplier.guilds.collect {
                database.serverConfigScope.getGuildConfig(it.id.toString()).channels[Channels.LOG_CHANNEL.name]?.let { channelId ->
                    supplier.getChannel(Snowflake(channelId)).asChannelOf<MessageChannel>().createMessage {
                        content = "KaironBot started!"
                    }
                } ?: it.getSystemChannel()
                    ?.createMessage {
                        content = "KaironBot started! To change the logging channel, use the /set_channel command"
                    }
            }
            schedulingManager.retrieveInterruptedTasks(supplier.guilds)
        }
    }

    @OptIn(PrivilegedIntent::class)
    suspend fun start() = coroutineScope {
        logger.info { "Starting KaironBot with profile $profile" }
        events.forEach {
            it.register()
            cacheManager.registerEvent(it.eventId)
            logger.info { "Registered ${it.eventId} event" }
        }
        // commands.forEach {
        //     it.registerCommand()
        //     it.registerCallback()
        //     logger.info { "Registered ${it.commandName} command" }
        // }

        launch { schedulingManager.launchTasks(cacheManager, kord) }

        cacheManager.createNewCollectionCache<BuildingWithBounty> { guildId, db ->
            db.buildingsScope.getBuildingsWithBounty(guildId.toString()).toList()
        }

        cacheManager.createNewCollectionCache<Item> { guildId, db ->
            db.itemsScope.getAllItems(guildId.toString()).toList().sortedBy { it.name }
        }

        cacheManager.createNewCollectionCache<LanguageProficiency> { guildId, db ->
            db.proficiencyScope.getLanguages(guildId.toString()).toList()
        }

        cacheManager.createNewCollectionCache<ToolProficiency> { guildId, db ->
            db.proficiencyScope.getToolProficiencies(guildId.toString()).toList()
        }

        launch {
            val gateway = ExternalGateway(cacheManager)
            gateway.open()
        }

        kord.login {
            intents += Intent.GuildMembers
            intents += Intent.GuildPresences
            intents += Intent.MessageContent
        }
    }

}

suspend fun main() {
    val kord = Kord(System.getenv("BOT_TOKEN")!!)
    val bot = KaironBot("wagham", kord)
    bot.start()
}
