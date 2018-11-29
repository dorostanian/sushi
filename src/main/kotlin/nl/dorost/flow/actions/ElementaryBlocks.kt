package nl.dorost.flow.actions

import com.github.kittinunf.fuel.Fuel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import nl.dorost.flow.core.Action
import org.slf4j.LoggerFactory
import java.lang.RuntimeException


val elementaryActions = mutableListOf(
    Action().apply {
        name = "Logger"
        type = "log"
        description = "Logs whatever comes to the block as input!"
        act = { input ->
            listeners.forEach { it.updateReceived(message = "Action id '${id}': ${name}. Input Value: ${input}") }
            input
        }
    },
    Action().apply {
        name = "Constant Producer"
        type = "constant"
        description = "Passes the constant value passed as parameter <code>const</code>."
        act = { input ->
            listeners.forEach { it.updateReceived(message = "Action id '${id}': ${name}. Input Value: ${input}") }
            val constValue = params!!["const"] ?: throw UnsatisfiedParamsException("Parameter const not found!")
            mutableMapOf("value" to constValue)
        }
        params = mutableMapOf("const" to "")
    },
    Action().apply {
        name = "Makes http POST call"
        description = "Make the post call based on the parameters given. Passes the <code>response</code>."
        type = "http-post"
        act = { input ->
            listeners.forEach { it.updateReceived(message = "Action id '${id}': ${name}. Input Value: ${input}") }
            val url = params!!["url"] ?: throw UnsatisfiedParamsException("Parameter url not found!")
            val contentType = params!!["CONTENT_TYPE"] ?: "application/json"
            val accept = params!!["Accept"] ?: "application/json"
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
        act = { input ->
            listeners.forEach { it.updateReceived(message = "Action id '${id}': ${name}. Input Value: ${input}") }
            val url = params!!["url"] ?: throw UnsatisfiedParamsException("Parameter url not found!")
            val contentType = params!!["CONTENT_TYPE"] ?: "application/json"
            val accept = params!!["Accept"] ?: "application/json"
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
        act = { input ->
            listeners.forEach { it.updateReceived(message = "Action id '${id}': ${name}. Input Value: ${input}") }
            val value = params!!["value"] ?: throw UnsatisfiedParamsException("Value not found!")
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