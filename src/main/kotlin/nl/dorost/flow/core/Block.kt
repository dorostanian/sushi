package nl.dorost.flow.core

import nl.dorost.flow.ExpectedParamNotPresentException
import nl.dorost.flow.MissingMappingValueException
import org.slf4j.LoggerFactory


val LOG = LoggerFactory.getLogger("FlowEngine")

abstract class Block(
    open val name: String,
    open val id: String?,
    open val type: String,
    open var input: MutableMap<String, Any> = mutableMapOf(),
    open val params: MutableMap<String, String> = mutableMapOf()
) {
    abstract fun run(flowEngine: FlowEngine)
}

data class Container(
    var firstBlock: String? = null,
    var lastBlock: String? = null,
    override val name: String,
    override val id: String? = null,
    override val type: String,
    override var input: MutableMap<String, Any> = mutableMapOf(),
    override val params: MutableMap<String, String> = mutableMapOf()
) : Block(name, id, type, input, params) {

    override fun run(flowEngine: FlowEngine) {
        val flows = flowEngine.flows
        LOG.debug("Executing Container id '$id', first is $firstBlock")
        flows.first { it.id == firstBlock }.run(flowEngine)
    }
}

data class Action(
    var act: ((action: Action) -> Map<String, Any>)? = null,
    val nextBlocks: MutableList<String> = mutableListOf(),
    val returnAfterExec: Boolean = false,
    override val name: String,
    override val id: String? = null,
    override val type: String,
    override var input: MutableMap<String, Any> = mutableMapOf(),
    override val params: MutableMap<String, String> = mutableMapOf()
) : Block(name, id, type, input, params) {

    override fun run(flowEngine: FlowEngine) {
        val flows = flowEngine.flows
        val output = this.act!!.invoke(this)
        LOG.debug("Executed Action id '$id', Name: '$name', Type: '$type', Output was: $output")

        if (this.returnAfterExec) {
            flowEngine.returnedBlockId = this.id
            flowEngine.returnValue = output as MutableMap<String, Any>
            return
        }
        nextBlocks.map { nextId ->
            flows.first { it.id == nextId }
        }.forEach { nextBlock ->
            nextBlock.input = output.toMutableMap()
            nextBlock.run(flowEngine)
        }
    }
}

data class Branch(
    val mapping: HashMap<String, String>,
    override val name: String,
    override val id: String? = null,
    override val type: String = "normal",
    override var input: MutableMap<String, Any> = mutableMapOf(),
    override val params: MutableMap<String, String>
) : Block(name, id, type, input, params) {
    override fun run(flowEngine: FlowEngine) {
        val flows = flowEngine.flows
        val variableToLook =
            params.get("var-name") ?: throw ExpectedParamNotPresentException("Parameter var-name is not specified!")
        val valueToLook = mapping.get(input.get(variableToLook))
            ?: throw MissingMappingValueException("No mapping specified for the value")

        LOG.debug("Executing Branch id '$id', Inputs: ${this.input}, Params: ${this.params}, Branching to: $valueToLook")
        flows.first { it.id == valueToLook }.run(flowEngine)
    }
}
