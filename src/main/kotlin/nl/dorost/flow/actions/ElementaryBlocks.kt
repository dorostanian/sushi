package nl.dorost.flow.actions

import nl.dorost.flow.Action
import org.slf4j.LoggerFactory
import java.lang.RuntimeException

val LOG = LoggerFactory.getLogger("ElementaryBlocks")


val elementaryActions = mapOf<String, (action: Action) -> Map<String, Any>>(
    "log" to { action: Action ->
        LOG.info("Action id '${action.id}': ${action.name}. Input Value: ${action.input}, Params: ${action.params}")
        mapOf<String, Any>()
    },
    "constant" to { action: Action ->
//        LOG.info("Action id '${action.id}': ${action.name}. Input Value: ${action.input}, Params: ${action.params}")
        val constValue = action.params["const"]
        if (constValue==null)
            throw UnsatisfiedParamsException("Parameter const not found!")
        mapOf("value" to constValue)

    }
)

class UnsatisfiedParamsException(msg: String): RuntimeException(msg)