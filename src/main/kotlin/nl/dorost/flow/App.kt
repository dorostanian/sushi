package nl.dorost.flow

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.auth.oauth2.GoogleCredentials
import com.natpryce.konfig.*
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.response.respondFile
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import mu.KotlinLogging
import nl.dorost.flow.core.*
import nl.dorost.flow.dao.BlocksDaoImpl
import nl.dorost.flow.dao.UserDaoImpl
import nl.dorost.flow.dto.UserDto
import nl.dorost.flow.utils.ResponseMessage
import java.io.File
import java.io.FileInputStream
import java.util.*


val googleClientId = Key("google.clientId", stringType)
val googleClientSecret = Key("google.clientSecret", stringType)
val sessionSecretSignKey = Key("session.secretSignKey", stringType)
val projectId = Key("project.Id", stringType)
val blockKind = Key("blocks.kind", stringType)
val usersKind = Key("users.kind", stringType)

val config = systemProperties() overriding
        EnvironmentVariables() overriding
        ConfigurationProperties.fromFile(
            File(Thread.currentThread().contextClassLoader.getResource("default.properties").path)
        )

fun main(args: Array<String>) {

    val credentials  = authWithJson(Thread.currentThread().contextClassLoader.getResource("service-account.json").path)
    val blocksDao = BlocksDaoImpl(credentials, projectId = config[projectId], kind = config[blockKind])
    val usersDao = UserDaoImpl(credentials, projectId = config[projectId], kind = config[usersKind])

    val channel = Channel<Pair<MessageType, String>>()


    val flowEngine = FlowEngine()

    flowEngine.registerListeners(
        listOf(
            object : BlockListener {
                val LOG = KotlinLogging.logger("LoggerListener")
                override fun updateReceived(context: MutableMap<String, Any>?, message: String?, type: MessageType) {
                    val msg = "Message: $message"
                    LOG.info { msg }
                    GlobalScope.launch {
                        channel.send(type to (message ?: ""))
                    }
                }
            }
        )
    )

    flowEngine.registerSecondaryActionsFromDB(blocksDao)

    val myUser = UserDto(
        id = 3456789,
        name = "Amin Dorostanian"
    )

    val objectMapper = ObjectMapper()


    val server = embeddedServer(Netty, port = 8080) {

        install(WebSockets)

        routing {

            webSocket("/ws") {
                while (true) {
                    val data = channel.receive()
                    outgoing.send(Frame.Text(objectMapper.writeValueAsString(data)))
                }
            }

            get("/") {
                call.respondFile(File(Thread.currentThread().contextClassLoader.getResource("static/index.html").path))
            }

            static("/") {
                resources("static")
            }

            post("/tomlToDigraph") {
                val tomlString = call.receiveText()
                val digraph: String?
                try {
                    flowEngine.flows.clear()
                    val blocks = flowEngine.tomlToBlocks(tomlString)
                    flowEngine.wire(blocks, blocksDao, myUser)
                    digraph = flowEngine.blocksToDigraph()
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
                                    ?: "Something went wrong in the server side! ${e.message}"
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


            get("/addAction/{actionType}") {
                val actionType = call.parameters["actionType"]
                val registeredBlock = flowEngine.registeredActions.plus(flowEngine.secondaryActions).firstOrNull { it.type == actionType }

                registeredBlock?.let { action ->
                    flowEngine.flows.add(
                        action.apply {
                            id = UUID.randomUUID().toString()
                        }
                    )
                    if (flowEngine.flows.size==1)
                        (flowEngine.flows.first() as Action).source = true
                    flowEngine.wire(flowEngine.flows, blocksDao, myUser)
                    val toml = flowEngine.blocksToToml(flowEngine.flows)
                    val digraph = flowEngine.blocksToDigraph()
                    call.respond(
                        HttpStatusCode.OK,
                        objectMapper.writeValueAsString(
                            ResponseMessage(
                                responseLog = "Successfully added new action to flow!",
                                tomlData = toml,
                                digraphData = digraph
                            )
                        )
                    )
                }


            }

            post("/executeFlow") {
                val tomlString = call.receiveText()
                val blocks = flowEngine.tomlToBlocks(tomlString)

                flowEngine.wire(blocks, blocksDao, myUser)
                flowEngine.executeFlow()

                call.respond(
                    HttpStatusCode.OK,
                    objectMapper.writeValueAsString(
                        ResponseMessage(
                            responseLog = "Started flow execution successfully!",
                            tomlData = tomlString,
                            digraphData = flowEngine.blocksToDigraph()
                        )
                    )

                )
            }

            get("/getLibrary") {
                call.respond(
                    HttpStatusCode.OK,
                    objectMapper.writeValueAsString(
                        ResponseMessage(
                            responseLog = "Fetched library of registered actions!",
                            library = flowEngine.registeredActions.plus(flowEngine.secondaryActions)
                        )
                    )
                )
            }

            get("/deleteBlock/{blockId}") { pipelineContext ->
                val actionId = call.parameters["blockId"]

                flowEngine.flows.removeIf { it.id == actionId }
                flowEngine.flows.forEach { block ->
                    when (block) {
                        is Action -> {
                            block.nextBlocks.removeIf { it == actionId }
                        }
                        is Branch -> {
                            block.mapping.entries.removeIf { it.value == actionId }
                        }
                    }
                }
                // Rebuild graph and send back the response

                val digraph = flowEngine.blocksToDigraph()
                val tomlText: String = flowEngine.blocksToToml(flowEngine.flows)
                call.respond(
                    HttpStatusCode.OK,
                    objectMapper.writeValueAsString(
                        ResponseMessage(
                            responseLog = "Action Removed successfully!",
                            digraphData = digraph,
                            tomlData = tomlText
                        )
                    )
                )


            }
        }
    }
    server.start(wait = true)
}

fun authWithJson(jsonPath: String) = GoogleCredentials.fromStream(FileInputStream(jsonPath))
    .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))
