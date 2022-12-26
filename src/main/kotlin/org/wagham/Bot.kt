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
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.wagham.annotations.BotCommand
import org.wagham.commands.Command
import org.wagham.commands.MSCommand
import org.wagham.components.CacheManager
import org.wagham.config.Channels
import org.wagham.db.KabotMultiDBClient
import org.wagham.db.models.MongoCredentials
import java.io.File
import kotlin.reflect.full.primaryConstructor

class WaghamBot(
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

    private fun autowireCommands() = Reflections("org.wagham.commands")
        .getTypesAnnotatedWith(BotCommand::class.java)
        .map { it.kotlin }
        .map {
            it.primaryConstructor!!.call(kord, database, cacheManager) as Command
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
    }

    @OptIn(PrivilegedIntent::class)
    suspend fun start() {
        logger.info { "Starting WaghamBot" }
        commands.forEach{
            it.registerCommand()
            it.registerCallback()
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
    val bot = WaghamBot(kord)
    bot.start()
}
