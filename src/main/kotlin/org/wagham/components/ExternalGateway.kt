package org.wagham.components

import dev.kord.common.entity.Snowflake
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.wagham.entities.channels.ExpUpdate
import org.wagham.entities.channels.RegisteredSession
import org.wagham.entities.channels.UpdateGuildAttendanceMessage
import org.wagham.events.AssignItemAfterSessionEvent
import org.wagham.events.AssignItemOnLevelUpEvent
import org.wagham.events.DailyAttendanceEvent

class ExternalGateway(
    private val cacheManager: CacheManager
) {

    /**
     * A POST route that should receive a message whenever a new session is registered.
     * It receives a [RegisteredSession] in the body and will ship it to the [AssignItemAfterSessionEvent] event and
     * the [DailyAttendanceEvent] event.
     */
    private fun Routing.registeredSessionHandler() = post("/session") {
        println("Received Session")
        val registeredSession = call.receive<RegisteredSession>()
        cacheManager.sendToChannel<AssignItemAfterSessionEvent, RegisteredSession>(registeredSession)
        cacheManager.sendToChannel<DailyAttendanceEvent, UpdateGuildAttendanceMessage>(
            UpdateGuildAttendanceMessage(Snowflake(registeredSession.guildId))
        )
        call.respond("ok")
    }

    /**
     * A POST route that should receive a message whenever a new session is registered.
     * It receives a [ExpUpdate] in the body and will ship it to the [AssignItemOnLevelUpEvent] event.
     */
    private fun Routing.levelUpHandler() = post("/levelUp") {
        println("Received LevelUp")
        val update = call.receive<ExpUpdate>()
        cacheManager.sendToChannel<AssignItemOnLevelUpEvent, ExpUpdate>(update)
        call.respond("ok")
    }

    /**
     * Configures the Ktor server with JSON content negotiation and routes.
     */
    private fun moduleConfiguration(): Application.() -> Unit = {
        install(ContentNegotiation) {
            json()
        }
        routing {
            registeredSessionHandler()
            levelUpHandler()
        }
    }

    /**
     * Opens the gateway for external communication.
     */
    fun open() {
        embeddedServer(CIO, port = 8617, host = "0.0.0.0", module = moduleConfiguration())
            .start(wait = true)
    }

}