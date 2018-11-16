package nl.dorost.flow

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.default
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import nl.dorost.flow.core.FlowEngine

fun main(args: Array<String>) {
    val flowEngine = FlowEngine()
    val objectMapper = ObjectMapper()

    val server = embeddedServer(Netty, port = 8080) {
        routing {

            static("/"){
                resources("static")
                default("static/index.html")
            }

            post("/tomlToDigraph") {
                val tomlString = call.receiveText()
                val responseMessage = flowEngine.tomlToDigraph(tomlString)
                call.respond(
                    responseMessage.httpCode, objectMapper.writeValueAsString(responseMessage)
                )
            }
        }
    }
    server.start(wait = true)
}