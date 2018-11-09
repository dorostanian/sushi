package nl.dorost.flow.actions

import nl.dorost.flow.Action
import nl.dorost.flow.Block
import nl.dorost.flow.Branch
import nl.dorost.flow.Container
import org.slf4j.LoggerFactory

val LOG = LoggerFactory.getLogger("ElementaryBlocks")


val elementaryBlocks = mutableListOf<Block>(
    Action(
        name = "Print Some Logs",
        type = "log"
    ).apply {
        act = { input: Map<String, Any> ->
            LOG.info("Action: $name. Input Value: $input, Params: $params")
            mapOf()
        }
    },
    Action(
        name = "Spits out constant value",
        type = "constant"
    ).apply {
        act = { input: Map<String, Any> ->
            LOG.info("Action: $name. Input Value: $input, Params: $params")
            mapOf()
        }
    },
    Container(
        name = "A normal container.",
        type = "normal"
    )
)
