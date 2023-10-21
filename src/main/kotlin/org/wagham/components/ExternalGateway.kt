package org.wagham.components

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.wagham.entities.channels.RegisteredSession
import org.wagham.events.AssignItemAfterSessionEvent

class ExternalGateway(
    private val cacheManager: CacheManager
) {

    private fun moduleConfiguration(): Application.() -> Unit = {
        install(ContentNegotiation) {
            json()
        }
        routing {
            post("/session") {
                val registeredSession = call.receive<RegisteredSession>()

                cacheManager.sendToChannel<AssignItemAfterSessionEvent, RegisteredSession>(registeredSession)

                call.respond("ok")
            }
        }
    }

    fun open() {
        embeddedServer(CIO, port = 8617, host = "0.0.0.0", module = moduleConfiguration())
            .start(wait = true)
    }

}