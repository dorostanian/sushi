package nl.dorost.flow

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.auth.oauth2.GoogleCredentials
import com.natpryce.konfig.*
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.features.DefaultHeaders
import io.ktor.features.origin
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.request.host
import io.ktor.request.port
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.response.respondFile
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.sessions.*
import io.ktor.util.hex
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import mu.KotlinLogging
import nl.dorost.flow.core.*
import nl.dorost.flow.dao.*
import nl.dorost.flow.dto.UserDto
import nl.dorost.flow.utils.ResponseMessage
import java.io.File
import java.io.FileInputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap


val googleClientId = Key("google.clientId", stringType)
val googleClientSecret = Key("google.clientSecret", stringType)
val sessionSecretSignKey = Key("session.secretSignKey", stringType)
val projectId = Key("project.Id", stringType)
val blockKind = Key("blocks.kind", stringType)
val usersKind = Key("users.kind", stringType)
val serviceAccountPath = Key("service-account", stringType)


var googleOauthProvider: OAuthServerSettings.OAuth2ServerSettings? = null

class SushiSession(val userId: String)

val appLogger = KotlinLogging.logger("AppLogger")


fun main(args: Array<String>) {

    var config: Configuration? = null
    var blocksDao: BlocksDao? = null
    var usersDao: UsersDao? = null

    val staticUserId = UUID.randomUUID().toString()

    getFile("default.properties")?.let {
        config = systemProperties() overriding
                EnvironmentVariables() overriding
                ConfigurationProperties.fromFile(it)

        googleOauthProvider = OAuthServerSettings.OAuth2ServerSettings(
            name = "google",
            authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
            accessTokenUrl = "https://www.googleapis.com/oauth2/v3/token",
            requestMethod = HttpMethod.Post,
            clientId = config!![googleClientId],
            clientSecret = config!![googleClientSecret],
            defaultScopes = listOf("profile", "email")
        )
        println(it)
        val credentials = authWithJson(getFilePath(config!![serviceAccountPath]))
        blocksDao = BlocksDaoImpl(credentials, projectId = config!![projectId], kind = config!![blockKind])
        usersDao = UserDaoImpl(credentials, projectId = config!![projectId], kind = config!![usersKind])
    } ?: run {
        blocksDao = BlocksMemoryDaoImpl()
        usersDao = UserMemoryDaoImpl()
    }


    val channel = Channel<Pair<MessageType, String>>()

    val usersFlows: ConcurrentHashMap<String, FlowEngine> = ConcurrentHashMap<String, FlowEngine>()

    val objectMapper = ObjectMapper()


    val server = embeddedServer(Netty, port = 8080) {

        install(WebSockets)

        install(DefaultHeaders)

        googleOauthProvider?.let {
            install(Sessions) {
                cookie<SushiSession>("SushiSession") {
                    val secretSignKey = hex(config!![sessionSecretSignKey])
                    transform(SessionTransportTransformerMessageAuthentication(secretSignKey))
                }

            }
            install(Authentication) {
                oauth("google-oauth") {
                    client = HttpClient(Apache)
                    providerLookup = { googleOauthProvider }
                    urlProvider = {
                        redirectUrl("/login")
                    }
                }
            }
        }
        routing {

            webSocket("/ws") {
                while (true) {
                    val data = channel.receive()
                    outgoing.send(Frame.Text(objectMapper.writeValueAsString(data)))
                }
            }

            get("/") {


                googleOauthProvider?.let {
                    call.sessions.get<SushiSession>()?.let {
                        call.respondFile(File(getFilePath("static/index.html")))
                    } ?: call.respondRedirect("/sign-in")
                } ?: call.respondFile(File(getFilePath("static/index.html")))
            }

            static("/") {
                resources("static")
            }


            get("/sign-in") {
                call.respondFile(File(getFilePath("static/sign-in.html")))
            }
            post("/tomlToDigraph") {

                googleOauthProvider?.let {
                    call.sessions.get<SushiSession>()?.let { sushiSession ->
                        val sessionId = sushiSession.userId
                        appLogger.info { "Session ID is $sessionId}" }
                        val flowEngine = usersFlows[sessionId]!!
                        val tomlString = call.receiveText()
                        val digraph: String?
                        try {
                            flowEngine.flows.clear()
                            val blocks = flowEngine.tomlToBlocks(tomlString)
                            val currentUser = usersDao!!.getUserBySessionId(sushiSession.userId)

                            flowEngine.wire(blocks, blocksDao, currentUser)
                            digraph = flowEngine.blocksToDigraph()
                            call.respond(
                                HttpStatusCode.OK,
                                objectMapper.writeValueAsString(
                                    ResponseMessage(
                                        responseLog = "Successfully converted TOML to Digraph.",
                                        digraphData = digraph,
                                        blocksIds = flowEngine.flows.map { it.id!! }
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
                    } ?: call.respond("You are not signed-in!")
                } ?: run {
                    val flowEngine = usersFlows[staticUserId]!!
                    val tomlString = call.receiveText()
                    val digraph: String?
                    try {
                        flowEngine.flows.clear()
                        val blocks = flowEngine.tomlToBlocks(tomlString)
                        val currentUser = usersDao!!.getUserBySessionId(staticUserId)

                        flowEngine.wire(blocks, blocksDao, currentUser)
                        digraph = flowEngine.blocksToDigraph()
                        call.respond(
                            HttpStatusCode.OK,
                            objectMapper.writeValueAsString(
                                ResponseMessage(
                                    responseLog = "Successfully converted TOML to Digraph.",
                                    digraphData = digraph,
                                    blocksIds = flowEngine.flows.map { it.id!! }
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

            }

            put("/editBlock") {

                googleOauthProvider?.let {

                    call.sessions.get<SushiSession>()?.let { sushiSession ->
                        val flowEngine = usersFlows[sushiSession.userId]!!

                        val actionStr = call.receiveText()
                        val actionToUpdate = objectMapper.readValue(actionStr, Action::class.java)
                        (flowEngine.flows.first { it.id == actionToUpdate.id.toString() } as Action).apply {
                            name = actionToUpdate.name
                            params = actionToUpdate.params
                            source = actionToUpdate.source
                            returnAfterExec = actionToUpdate.returnAfterExec
                            nextBlocks = actionToUpdate.nextBlocks
                        }
                        val toml = flowEngine.blocksToToml(flowEngine.flows)
                        val digraph = flowEngine.blocksToDigraph()
                        call.respond(
                            HttpStatusCode.OK,
                            objectMapper.writeValueAsString(
                                ResponseMessage(
                                    responseLog = "Successfully edited action in the flow!",
                                    tomlData = toml,
                                    digraphData = digraph,
                                    blocksIds = flowEngine.flows.map { it.id!! }
                                )
                            )
                        )
                    } ?: call.respond("You are not signed-in!")
                } ?: run {
                    val flowEngine = usersFlows[staticUserId]!!

                    val actionStr = call.receiveText()
                    val actionToUpdate = objectMapper.readValue(actionStr, Action::class.java)
                    (flowEngine.flows.first { it.id == actionToUpdate.id.toString() } as Action).apply {
                        name = actionToUpdate.name
                        params = actionToUpdate.params
                        source = actionToUpdate.source
                        returnAfterExec = actionToUpdate.returnAfterExec
                        nextBlocks = actionToUpdate.nextBlocks
                    }
                    val toml = flowEngine.blocksToToml(flowEngine.flows)
                    val digraph = flowEngine.blocksToDigraph()
                    call.respond(
                        HttpStatusCode.OK,
                        objectMapper.writeValueAsString(
                            ResponseMessage(
                                responseLog = "Successfully edited action in the flow!",
                                tomlData = toml,
                                digraphData = digraph,
                                blocksIds = flowEngine.flows.map { it.id!! }
                            )
                        )
                    )
                }
            }


            get("/getAction/{actionId}") {
                googleOauthProvider?.let {


                    call.sessions.get<SushiSession>()?.let { sushiSession ->
                        val flowEngine = usersFlows[sushiSession.userId]!!


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
                    } ?: call.respond("You are not signed-in!")
                } ?: run {
                    val flowEngine = usersFlows[staticUserId]!!

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


            get("/addAction/{actionType}") {

                googleOauthProvider?.let {

                    call.sessions.get<SushiSession>()?.let { sushiSession ->
                        val flowEngine = usersFlows[sushiSession.userId]!!
                        val actionType = call.parameters["actionType"]
                        val registeredBlock =
                            flowEngine.registeredActions.plus(flowEngine.secondaryActions)
                                .firstOrNull { it.type == actionType }

                        registeredBlock?.let { action ->
                            flowEngine.flows.add(
                                action.apply {
                                    id = UUID.randomUUID().toString()
                                }
                            )
                            if (flowEngine.flows.size == 1)
                                (flowEngine.flows.first() as Action).source = true

                            val currentUser = usersDao!!.getUserBySessionId(sushiSession.userId)

                            flowEngine.wire(flowEngine.flows, blocksDao, currentUser)
                            val toml = flowEngine.blocksToToml(flowEngine.flows)
                            val digraph = flowEngine.blocksToDigraph()
                            call.respond(
                                HttpStatusCode.OK,
                                objectMapper.writeValueAsString(
                                    ResponseMessage(
                                        responseLog = "Successfully added new action to flow!",
                                        tomlData = toml,
                                        digraphData = digraph,
                                        blocksIds = flowEngine.flows.map { it.id!! }
                                    )
                                )
                            )
                        }
                    } ?: call.respond("You are not signed-in!")
                } ?: run {
                    val flowEngine = usersFlows[staticUserId]!!
                    val actionType = call.parameters["actionType"]
                    val registeredBlock =
                        flowEngine.registeredActions.plus(flowEngine.secondaryActions)
                            .firstOrNull { it.type == actionType }

                    registeredBlock?.let { action ->
                        flowEngine.flows.add(
                            action.apply {
                                id = UUID.randomUUID().toString()
                            }
                        )
                        if (flowEngine.flows.size == 1)
                            (flowEngine.flows.first() as Action).source = true

                        val currentUser = usersDao!!.getUserBySessionId(staticUserId)

                        flowEngine.wire(flowEngine.flows, blocksDao, currentUser)
                        val toml = flowEngine.blocksToToml(flowEngine.flows)
                        val digraph = flowEngine.blocksToDigraph()
                        call.respond(
                            HttpStatusCode.OK,
                            objectMapper.writeValueAsString(
                                ResponseMessage(
                                    responseLog = "Successfully added new action to flow!",
                                    tomlData = toml,
                                    digraphData = digraph,
                                    blocksIds = flowEngine.flows.map { it.id!! }
                                )
                            )
                        )
                    }
                }

            }


            get("/addBranch") {

                googleOauthProvider?.let {

                    call.sessions.get<SushiSession>()?.let { sushiSession ->
                        val flowEngine = usersFlows[sushiSession.userId]!!

                        flowEngine.flows.add(
                            Branch().apply {
                                name = "New Branch"
                                id = UUID.randomUUID().toString()
                                on = "{specify-variable-name}"
                            }
                        )
                        flowEngine.wire(flowEngine.flows)
                        val toml = flowEngine.blocksToToml(flowEngine.flows)
                        val digraph = flowEngine.blocksToDigraph()
                        call.respond(
                            HttpStatusCode.OK,
                            objectMapper.writeValueAsString(
                                ResponseMessage(
                                    responseLog = "Successfully added new action to flow!",
                                    tomlData = toml,
                                    digraphData = digraph,
                                    blocksIds = flowEngine.flows.map { it.id!! }
                                )
                            )
                        )
                    } ?: call.respond("You are not signed-in!")
                } ?: run {
                    val flowEngine = usersFlows[staticUserId]!!

                    flowEngine.flows.add(
                        Branch().apply {
                            name = "New Branch"
                            id = UUID.randomUUID().toString()
                            on = "{specify-variable-name}"
                        }
                    )
                    flowEngine.wire(flowEngine.flows)
                    val toml = flowEngine.blocksToToml(flowEngine.flows)
                    val digraph = flowEngine.blocksToDigraph()
                    call.respond(
                        HttpStatusCode.OK,
                        objectMapper.writeValueAsString(
                            ResponseMessage(
                                responseLog = "Successfully added new action to flow!",
                                tomlData = toml,
                                digraphData = digraph,
                                blocksIds = flowEngine.flows.map { it.id!! }
                            )
                        )
                    )
                }

            }

            post("/executeFlow") {

                googleOauthProvider?.let {

                    call.sessions.get<SushiSession>()?.let { sushiSession ->
                        val flowEngine = usersFlows[sushiSession.userId]!!

                        val tomlString = call.receiveText()
                        val blocks = flowEngine.tomlToBlocks(tomlString)

                        val currentUser = usersDao!!.getUserBySessionId(sushiSession.userId)
                        flowEngine.wire(blocks, blocksDao, currentUser)
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

                    } ?: call.respond("You are not signed-in!")
                } ?: run {
                    val flowEngine = usersFlows[staticUserId]!!

                    val tomlString = call.receiveText()
                    val blocks = flowEngine.tomlToBlocks(tomlString)

                    val currentUser = usersDao!!.getUserBySessionId(staticUserId)
                    flowEngine.wire(blocks, blocksDao, currentUser)
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
            }

            get("/getLibrary") {
                googleOauthProvider?.let {
                    call.sessions.get<SushiSession>()?.let { sushiSession ->
                        var flowEngine = usersFlows.get(sushiSession.userId)
                        if (flowEngine == null) {
                            flowEngine = FlowEngine()

                            flowEngine.registerListeners(
                                listOf(
                                    object : BlockListener {
                                        val LOG = KotlinLogging.logger("LoggerListener")
                                        override fun updateReceived(
                                            context: MutableMap<String, Any>?,
                                            message: String?,
                                            type: MessageType
                                        ) {
                                            val msg = "Message: $message"
                                            LOG.info { msg }
                                            GlobalScope.launch {
                                                channel.send(type to (message ?: ""))
                                            }
                                        }
                                    }
                                )
                            )

                            flowEngine.registerSecondaryActionsFromDB(blocksDao!!)

                            usersFlows[sushiSession.userId] = flowEngine
                        }

                        call.respond(
                            HttpStatusCode.OK,
                            objectMapper.writeValueAsString(
                                ResponseMessage(
                                    responseLog = "Fetched library of registered actions!",
                                    library = flowEngine.registeredActions.plus(flowEngine.secondaryActions)
                                )
                            )
                        )
                    } ?: call.respond("You are not signed-in!")
                } ?: run {
                    var flowEngine = usersFlows.get(staticUserId)
                    if (flowEngine == null) {
                        flowEngine = FlowEngine()

                        flowEngine!!.registerListeners(
                            listOf(
                                object : BlockListener {
                                    val LOG = KotlinLogging.logger("LoggerListener")
                                    override fun updateReceived(
                                        context: MutableMap<String, Any>?,
                                        message: String?,
                                        type: MessageType
                                    ) {
                                        val msg = "Message: $message"
                                        LOG.info { msg }
                                        GlobalScope.launch {
                                            channel.send(type to (message ?: ""))
                                        }
                                    }
                                }
                            )
                        )

                        flowEngine!!.registerSecondaryActionsFromDB(blocksDao!!)

                        usersFlows[staticUserId] = flowEngine!!
                    }

                    call.respond(
                        HttpStatusCode.OK,
                        objectMapper.writeValueAsString(
                            ResponseMessage(
                                responseLog = "Fetched library of registered actions!",
                                library = flowEngine!!.registeredActions.plus(flowEngine!!.secondaryActions)
                            )
                        )
                    )
                }

            }

            get("/deleteBlock/{blockId}") {
                googleOauthProvider?.let {

                    call.sessions.get<SushiSession>()?.let { sushiSession ->
                        val flowEngine = usersFlows[sushiSession.userId]!!

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
                                    responseLog = "Block Removed successfully!",
                                    digraphData = digraph,
                                    tomlData = tomlText,
                                    blocksIds = flowEngine.flows.map { it.id!! }
                                )
                            )
                        )


                    } ?: call.respond("You are not signed-in!")
                } ?: run {
                    val flowEngine = usersFlows[staticUserId]!!

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
                                responseLog = "Block Removed successfully!",
                                digraphData = digraph,
                                tomlData = tomlText,
                                blocksIds = flowEngine.flows.map { it.id!! }
                            )
                        )
                    )

                }
            }



            get("/signOut") {
                call.sessions.get<SushiSession>()?.let { session ->
                    call.sessions.clear("SushiSession")
                    call.respondText("OK")

                }
                call.respondText("NOK")
            }



            googleOauthProvider?.let {

                authenticate("google-oauth") {
                    route("/login") {

                        googleOauthProvider?.let {

                            handle {
                                val principal = call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()
                                    ?: error("No principal")

                                appLogger.info("${principal.accessToken} expires in ${principal.expiresIn}")


                                val json = HttpClient(Apache).get<String>("https://www.googleapis.com/userinfo/v2/me") {
                                    header("Authorization", "Bearer ${principal.accessToken}")
                                }

                                val data: Map<String, Any?> = objectMapper.readValue(
                                    json,
                                    object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {})
                                appLogger.info("All the data we retrieved : $data")
                                val sessionId = data["id"] as String?
                                appLogger.info("Session id is $sessionId")

                                if (sessionId != null) {
                                    call.sessions.set("SushiSession", SushiSession(sessionId))
                                }

                                usersDao!!.getUserBySessionId(sessionId!!)?.let {
                                    appLogger.info { "User ${it.email} logged in!" }
                                } ?: kotlin.run {
                                    appLogger.info { "User ${data["name"]} created!" }
                                    usersDao!!.createUser(
                                        UserDto(
                                            id = null,
                                            sessionId = sessionId,
                                            name = data["name"] as String,
                                            email = ""
                                        )
                                    )
                                }


                                val flowEngine = FlowEngine()

                                flowEngine.registerListeners(
                                    listOf(
                                        object : BlockListener {
                                            val LOG = KotlinLogging.logger("LoggerListener")
                                            override fun updateReceived(
                                                context: MutableMap<String, Any>?,
                                                message: String?,
                                                type: MessageType
                                            ) {
                                                val msg = "Message: $message"
                                                LOG.info { msg }
                                                GlobalScope.launch {
                                                    channel.send(type to (message ?: ""))
                                                }
                                            }
                                        }
                                    )
                                )

                                flowEngine.registerSecondaryActionsFromDB(blocksDao!!)

                                usersFlows[sessionId] = flowEngine

                                call.respondRedirect("/")
                            }
                        }
                    }
                }
            } ?: run {

                appLogger.info { "Logging in without a google provider!" }

                usersDao!!.getUserBySessionId(staticUserId)?.let {
                    appLogger.info { "User ${it.email} logged in!" }
                } ?: run {
                    appLogger.info { "Creating user!" }
                    usersDao!!.createUser(
                        UserDto(
                            id = null,
                            sessionId = staticUserId,
                            name = "anonymous",
                            email = ""
                        )
                    )
                }


                val flowEngine = FlowEngine()

                flowEngine.registerListeners(
                    listOf(
                        object : BlockListener {
                            val LOG = KotlinLogging.logger("LoggerListener")
                            override fun updateReceived(
                                context: MutableMap<String, Any>?,
                                message: String?,
                                type: MessageType
                            ) {
                                val msg = "Message: $message"
                                LOG.info { msg }
                                GlobalScope.launch {
                                    channel.send(type to (message ?: ""))
                                }
                            }
                        }
                    )
                )

                flowEngine.registerSecondaryActionsFromDB(blocksDao!!)

                usersFlows[staticUserId] = flowEngine

            }
        }
    }
    server.start(wait = true)
}


private fun getFile(fileName: String): File? = try {
    File(getFilePath("tmp/default.properties"))
} catch (e: Exception) {
    null
}

private fun getFilePath(fileName: String) = Thread.currentThread().contextClassLoader.getResource(fileName).path

fun authWithJson(jsonPath: String) = GoogleCredentials.fromStream(FileInputStream(jsonPath))
    .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))


private fun ApplicationCall.redirectUrl(path: String): String {
    val defaultPort = if (request.origin.scheme == "http") 80 else 443
    val hostPort = request.host()!! + request.port().let { port -> if (port == defaultPort) "" else ":$port" }
    val protocol = request.origin.scheme
    return "$protocol://$hostPort$path"
}