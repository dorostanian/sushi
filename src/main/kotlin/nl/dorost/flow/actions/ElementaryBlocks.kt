package nl.dorost.flow.actions

import com.github.kittinunf.fuel.Fuel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.consumesAll
import nl.dorost.flow.core.Action
import org.slf4j.LoggerFactory
import java.lang.RuntimeException


val elementaryActions = mutableListOf(
    Action().apply {
        name = "Logger"
        type = "log"
        description = "Logs whatever comes to the block as input!"
        act = { input, action ->
            action.listeners.forEach { it.updateReceived(message = "Action id '${action.id}': ${action.name}. Input Value: ${input}, params: ${action.params}") }
            input
        }
    },
    Action().apply {
        name = "Constant Producer"
        type = "constant"
        description = "Passes the constant value passed as parameter <code>const</code>."
        act = { input, action ->
            action.listeners.forEach { it.updateReceived(message = "Action id '${action.id}': ${action.name}. Input Value: ${input}, params: ${action.params}") }
            val constValue = action.params!!["value"] ?: throw UnsatisfiedParamsException("Parameter const not found!")
            mutableMapOf("value" to constValue)
        }
        params = mutableMapOf("value" to "")
    },
    Action().apply {
        name = "Makes http POST call"
        description = "Make the post call based on the parameters given. Passes the <code>response</code>."
        type = "http-post"
        act = { input, action ->
            action.listeners.forEach { it.updateReceived(message = "Action id '${action.id}': ${action.name}. Input Value: ${input}") }
            val url = action.params!!["url"] ?: throw UnsatisfiedParamsException("Parameter url not found!")
            val contentType = action.params!!["CONTENT_TYPE"] ?: "application/json"
            val accept = action.params!!["Accept"] ?: "application/json"
            val body = input["body"] ?: UnsatisfiedParamsException("Missing body in the input!")

            val result = Fuel
                .post(url)
                .header("ContentType" to contentType, "Accept" to accept)
                .body(body as String)
                .responseString()
            mutableMapOf("response" to String(result.second.data))
        }
    },
    Action().apply {
        name = "Makes http GET call"
        description = "Make the get call based on the parameters given. Passes the <code>response</code>"
        type = "http-get"
        params = mutableMapOf("url" to "", "Accept" to "application/json", "ContentType" to "", "body" to "")
        act = { input , action->
            action.listeners.forEach { it.updateReceived(message = "Action id '${action.id}': ${action.name}. Input Value: ${input}") }
            val url = action.params!!["url"] ?: throw UnsatisfiedParamsException("Parameter url not found!")
            val contentType = action.params!!["CONTENT_TYPE"] ?: "application/json"
            val accept = action.params!!["Accept"] ?: "application/json"
            val result = Fuel.get(url)
                .header("ContentType" to contentType, "Accept" to accept)
                .responseString()
            mutableMapOf("response" to String(result.second.data))
        }
    },
    Action().apply {
        name = "Get JSON as input from the parameter <code>value<code> and pass it along."
        description = "Used when we want to pass an static JSON."
        params = mutableMapOf("value" to "")
        type = "json-in"
        act = { input, action ->
            action.listeners.forEach { it.updateReceived(message = "Action id '${action.id}': ${action.name}. Input Value: ${input}") }
            val value = action.params!!["value"] ?: throw UnsatisfiedParamsException("Value not found!")
            mutableMapOf("body" to value)
        }
    }
    // desgign a scenario
    // send email block
    // connect to telegram bot
    // Persistence blocks (GCP)
    // BigQuery, ....

)


class UnsatisfiedParamsException(msg: String) : RuntimeException(msg)

class UnsatisfiedInputException(msg: String) : RuntimeException(msg)