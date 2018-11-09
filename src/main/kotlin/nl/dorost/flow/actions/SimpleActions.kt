package nl.dorost.flow.actions

import nl.dorost.flow.Action

val logAction = Action(
    name = "Print Some Logs",
    type = "log"
).apply {
    act = { input: Map<String, Any>->
        println("Action: $name. Input Value: $input, Params: $params")
        mapOf()
    }
}
