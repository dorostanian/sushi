package nl.dorost.flow.core

import kotlinx.coroutines.*
import nl.dorost.flow.MissingMappingValueException
import org.slf4j.LoggerFactory


val LOG = LoggerFactory.getLogger("FlowEngine")

abstract class Block {
    var id: String? = null
    var name: String? = null
    var type: String? = null
    var dependencies: MutableList<String>? = null
    var output: Deferred<MutableMap<String, Any>>? = null
    var description: String? = null
    var started: Boolean = false

    abstract fun run(flowEngine: FlowEngine): Job
}


class Action(
    var nextBlocks: MutableList<String> = mutableListOf(),
    var returnAfterExec: Boolean = false,
    var source: Boolean = false,
    var params: MutableMap<String, String>? = null,
    var innnerBlocks: MutableList<Block>? = mutableListOf()
) : Block() {

    protected fun act(input: MutableMap<String, Any>): Deferred<MutableMap<String, Any>>? = null

    override fun run(
        flowEngine: FlowEngine
    ) = GlobalScope.launch {
        if (started) {
            LOG.debug("Action $id executions is already started!")
            return@launch
        }
        started = true
        LOG.debug("Executing Action id '$id', Name: '$name', Type: '$type', Output was: $output")

        val input = flowEngine.getDependentBlocks(this@Action).flatMap { block ->
            block.output?.await()?.entries!!.map { Pair(it.key, it.value) }
        }.toMap().toMutableMap()


        innnerBlocks?.let {
            val innerEngine = FlowEngine()
            innerEngine.wire(it)
            innerEngine.executeFlow(input)
            this@Action.output = innerEngine.returnValue
        } ?: run {
            this@Action.output = act(input)
        }


        if (returnAfterExec) {
            flowEngine.returnedBlockId = id
            flowEngine.returnValue = output
        }
        nextBlocks.map { nextId ->
            flowEngine.flows.first { it.id == nextId }
        }.forEach { nextBlock ->
            nextBlock.run(flowEngine)
        }
    }
}

class Branch(
    var mapping: MutableMap<String, String> = mutableMapOf(),
    var on: String? = null
) : Block() {

    override fun run(
        flowEngine: FlowEngine
    ) = GlobalScope.launch {
        if (started) {
            LOG.debug("Branch $id executions is already started!")
            return@launch
        }
        started = true
        val input = flowEngine.getDependentBlocks(this@Branch).flatMap { action ->
            action.output?.await()?.entries!!.map { Pair(it.key, it.value) }
        }.toMap().toMutableMap()


        this@Branch.output = async { input }

        if (type.equals(BRANCH_TYPE.ROUTER.toString(), true))
            mapping.putAll(flowEngine.flows.map { Pair(it.id!!, it.id!!) }.toMap())


        val blockIdToBranch = mapping[input[on]]
            ?: throw MissingMappingValueException("No mapping specified for the value ${input[on]}")

        val blockToGo = flowEngine.flows.first { it.id == blockIdToBranch }
        blockToGo.run(flowEngine)
        LOG.debug("Executing Branch id '$id', Branching to: $blockIdToBranch, now executing...")
    }

}

enum class BRANCH_TYPE {
    NORMAL, ROUTER
}


