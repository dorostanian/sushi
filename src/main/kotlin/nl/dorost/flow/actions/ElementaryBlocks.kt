package nl.dorost.flow.actions

import com.github.kittinunf.fuel.Fuel
import nl.dorost.flow.core.Action


val elementaryActions = mutableListOf(
    Action().apply {
        name = "Logger"
        type = "log"
        description = "Logs whatever comes to the block as input!"
        act = { input, action ->
            action.listeners.forEach { it.updateReceived(message = "LOG: Action id '${action.id}': ${action.name}. Input Value: ${input}, params: ${action.params}") }
            input
        }
    },
    Action().apply {
        name = "Constant Producer"
        type = "constant"
        description = "Passes the constant value passed as parameter <code>const</code>."
        act = { input, action ->
            action.listeners.forEach { it.updateReceived(message = "Action id '${action.id}': ${action.name}. Input Value: ${input}, params: ${action.params}") }
            val constValue =
                action.params["value"] ?: throw UnsatisfiedParamsException("Parameter not found for action Id: ${action.id}")
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
            val url = action.params["url"] ?: throw UnsatisfiedParamsException("Parameter url not found!")
            val contentType = action.params["CONTENT_TYPE"] ?: "application/json"
            val accept = action.params["Accept"] ?: "application/json"
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
        act = { input, action ->
            action.listeners.forEach { it.updateReceived(message = "Action id '${action.id}': ${action.name}. Input Value: ${input}") }
            val url = action.params["url"] ?: throw UnsatisfiedParamsException("Parameter url not found!")
            val contentType = action.params["CONTENT_TYPE"] ?: "application/json"
            val accept = action.params["Accept"] ?: "application/json"
            val result = Fuel.get(url)
                .header("ContentType" to contentType, "Accept" to accept)
                .responseString()
            mutableMapOf("response" to String(result.second.data))
        }
    },
    Action().apply {
        name = "JSON Input"
        description = "Get JSON as input from the parameter <code>value<code> and pass it along." +
                "Used when we want to pass an static JSON."
        params = mutableMapOf("value" to "")
        type = "json-in"
        act = { input, action ->
            action.listeners.forEach { it.updateReceived(message = "Action id '${action.id}': ${action.name}. Input Value: ${input}") }
            val value = action.params["value"] ?: throw UnsatisfiedParamsException("Value not found!")
            mutableMapOf("body" to value)
        }
    },
    Action().apply {
        name = "Delay"
        params = mutableMapOf("seconds" to "1")
        type = "delay"
        act = { input, action ->
            val secondsToWait = action.params["seconds"] ?: throw UnsatisfiedParamsException("Missing seconds param!")
            Thread.sleep(secondsToWait.toLong() * 1000)
            input
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

