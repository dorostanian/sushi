package nl.dorost.flow

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


    val server = embeddedServer(Netty, port = 8080) {
        routing {

            static("/"){
                resources("static")
                default("static/index.html")
            }

            post("/tomlToDigraph") {
                println("headers: " + call.request.headers)
                val tomlString = call.receiveText()
                val digraphConversion = flowEngine.tomlToDigraph(tomlString)
                call.respond(HttpStatusCode.OK, (digraphConversion))
            }
        }
    }
    server.start(wait = true)
}