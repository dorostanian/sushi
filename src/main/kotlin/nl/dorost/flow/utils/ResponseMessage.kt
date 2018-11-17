package nl.dorost.flow.utils

import nl.dorost.flow.core.Action
import nl.dorost.flow.core.Block

data class ResponseMessage(
    val responseLog: String,
    val digraphData: String? = null,
    val blockInfo: Block? = null,
    val tomlData: String? = null,
    val library: List<Action>? = null
)