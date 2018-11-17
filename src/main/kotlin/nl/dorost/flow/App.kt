package nl.dorost.flow

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.default
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import nl.dorost.flow.core.FlowEngine
import nl.dorost.flow.utils.ResponseMessage
import java.lang.Exception

fun main(args: Array<String>) {
    val flowEngine = FlowEngine()
    val objectMapper = ObjectMapper()

    val server = embeddedServer(Netty, port = 8080) {
        routing {

            static("/") {
                resources("static")
                default("static/index.html")
            }

            post("/tomlToDigraph") {
                val tomlString = call.receiveText()
                val digraph: String?
                try {
                    flowEngine.flows.clear()
                    val blocks = flowEngine.tomlToBlocks(tomlString)
                    flowEngine.wire(blocks)
                    digraph = flowEngine.blocksToDigraph(blocks)
                    flowEngine.wire(blocks)
                    call.respond(
                        HttpStatusCode.OK,
                        objectMapper.writeValueAsString(
                            ResponseMessage(
                                responseLog = "Successfully converted TOML to Digraph.",
                                digraphData = digraph
                            )
                        )
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        objectMapper.writeValueAsString(
                            ResponseMessage(
                                responseLog = e.message
                                    ?: "Something went wrong in the server side! ${e.localizedMessage}"
                            )
                        )
                    )
                }

            }

            get("/getAction/{actionId}") { pipelineContext ->
                val actionId = call.parameters["actionId"]
                val action = flowEngine.flows.firstOrNull { it.id == actionId }

                action?.let {
                    call.respond(
                        HttpStatusCode.OK,
                        objectMapper.writeValueAsString(
                            ResponseMessage(
                                blockInfo = it,
                                responseLog = "Successfully fetched action info for ${it.id}"
                            )
                        )
                    )
                } ?: run {
                    call.respond(
                        HttpStatusCode.NotFound,
                        objectMapper.writeValueAsString(
                            ResponseMessage(
                                responseLog = "Couldn't find action with id: ${actionId}"
                            )
                        )
                    )
                }

            }
        }
    }
    server.start(wait = true)
}