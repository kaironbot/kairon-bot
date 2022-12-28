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
import mu.KotlinLogging
import org.reflections.Reflections
import org.wagham.annotations.BotCommand
import org.wagham.annotations.BotEvent
import org.wagham.commands.Command
import org.wagham.components.CacheManager
import org.wagham.config.Channels
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.MongoCredentials
import org.wagham.events.Event
import kotlin.reflect.full.primaryConstructor

class WaghamBot(
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
    private val cacheManager = CacheManager(database)
    private val logger = KotlinLogging.logger {}
    private val commands: List<Command>
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
            it.primaryConstructor!!.call(kord, database, cacheManager) as Command
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
        kord.on<ReadyEvent> {
            this.guildIds.map {
                database.serverConfigScope.getGuildConfig(it.toString()).channels[Channels.LOG_CHANNEL.name]?.let { channelId ->
                    this.supplier.getChannel(Snowflake(channelId)).asChannelOf<MessageChannel>().createMessage {
                        content = "WaghamBot started!"
                    }
                } ?:  this.supplier.getGuild(it).getSystemChannel()
                    ?.createMessage {
                        content = "WaghamBot started! To change the logging channel, use the /set_channel command"
                    }
            }
        }
        commands = autowireCommands()
        events = autowireEvents()
    }

    @OptIn(PrivilegedIntent::class)
    suspend fun start() {
        logger.info { "Starting WaghamBot with profile $profile" }
        events.forEach {
            it.register()
            cacheManager.registerEvent(it.eventId)
            logger.info { "Registered ${it.eventId} event" }
        }
        commands.forEach {
            it.registerCommand()
            it.registerCallback()
            cacheManager.registerCommand(it.commandName)
            logger.info { "Registered ${it.commandName} command" }
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
    val bot = WaghamBot("wagham", kord)
    bot.start()
}
