package nl.dorost.flow.actions

import com.github.kittinunf.fuel.Fuel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import nl.dorost.flow.core.Action
import org.slf4j.LoggerFactory
import java.lang.RuntimeException

val LOG = LoggerFactory.getLogger("ElementaryBlocks")


val elementaryActions = mutableListOf<Action>(
    Action().apply {
        name = "Constant Producer",
        type = "constant",
        description = "Passes the constant value passed as parameter <code>const</code>.",
        act = { input ->
            GlobalScope.async {
                LOG.debug("Action id '${id}': ${name}. Input Value: ${input}, Params: ${params}")
                val constValue = params!!["const"] ?: throw UnsatisfiedParamsException("Parameter const not found!")
                mutableMapOf("value" to constValue as Any)
            }
        }
        params = mutableMapOf("const" to "")
    },
    Action().apply {
        name = "Makes http POST call",
        description = "Make the post call based on the parameters given. Passes the <code>response</code>.",
        type = "http-post",
        act = { input ->
            GlobalScope.async {
                val url = params!!["url"] ?: throw UnsatisfiedParamsException("Parameter url not found!")
                val contentType = params!!["CONTENT_TYPE"] ?: "application/json"
                val accept = params!!["Accept"] ?: "application/json"
                val body = input["body"] ?: UnsatisfiedParamsException("Missing body in the input!")

                val result = Fuel
                    .post(url)
                    .header("ContentType" to contentType, "Accept" to accept)
                    .body(body as String)
                    .responseString()
                mutableMapOf("response" to String(result.second.data) as Any)
            }
        }
    },
    Action(
        name = "Makes http GET call",
        description = "Make the get call based on the parameters given. Passes the <code>response</code>",
        type = "http-get",
        act = { action: Action ->
            val url = action.params["url"] ?: throw UnsatisfiedParamsException("Parameter url not found!")
            val contentType = action.params["CONTENT_TYPE"] ?: "application/json"
            val accept = action.params["Accept"] ?: "application/json"
            val result = Fuel.get(url)
                .header("ContentType" to contentType, "Accept" to accept)
                .responseString()
            mapOf("response" to String(result.second.data))
        },
        params = mutableMapOf("url" to "", "Accept" to "application/json", "ContentType" to "", "body" to "")
    ),
    Action(
        name = "Get JSON as input from the parameter <code>value<code> and pass it along.",
        description = "Used when we want to pass an static JSON.",
        type = "json-in",
        act = { action: Action ->
            val value = action.params["value"] ?: throw UnsatisfiedParamsException("Value not found!")
            mapOf("body" to value)
        }
    )
    // desgign a scenario
    // send email block
    // connect to telegram bot
    // Persistence blocks (GCP)
    // BigQuery, ....

)


class UnsatisfiedParamsException(msg: String) : RuntimeException(msg)

class UnsatisfiedInputException(msg: String) : RuntimeException(msg)