package nl.dorost.flow.core

import kotlinx.coroutines.*
import nl.dorost.flow.MissingMappingValueException


abstract class Block {
    var id: String? = null
    var name: String? = null
    var type: String? = null
    var dependencies: MutableList<String>? = null
    var output: Deferred<MutableMap<String, Any>>? = null
    var description: String? = null
    var started: Boolean = false
    var skipped: Boolean = false
    var listeners: List<BlockListener> = mutableListOf()

    abstract fun run(flowEngine: FlowEngine): Job
}


open class Action(
    var nextBlocks: MutableList<String> = mutableListOf(),
    var returnAfterExec: Boolean = false,
    var source: Boolean = false,
    var params: MutableMap<String, String>? = null,
    var innnerBlocks: MutableList<Block>? = null,
    var act: ((input: MutableMap<String, Any>, action: Action) -> MutableMap<String, Any>)? = null
) : Block() {

    override fun run(
        flowEngine: FlowEngine
    ) = GlobalScope.launch {
        if (started) {
            listeners.forEach { it.updateReceived(message = "Action $id executions is already started!") }
            return@launch
        }
        started = true
        listeners.forEach { it.updateReceived(message = "Executing Action id '$id', Name: '$name', Type: '$type'") }

        val input = flowEngine.getDependentBlocks(this@Action).flatMap { block ->
            block.output?.await()?.entries!!.map { Pair(it.key, it.value) }
        }.toMap().toMutableMap()


        innnerBlocks?.let {
            //            listeners.forEach { it.updateReceived(message = "Running inside inner engine for action ${this@Action.type}") }
            val innerEngine = FlowEngine()
            innerEngine.registerListeners(listeners)
            innerEngine.wire(it)
            innerEngine.executeFlow(input)
            innerEngine.await()
            this@Action.output = async { innerEngine.returnValue!! }
        } ?: run {
            this@Action.output = GlobalScope.async { act!!.invoke(input, this@Action) }
        }


        if (returnAfterExec) {
            flowEngine.returnedBlockId = id
            flowEngine.returnValue = this@Action.output!!.await()
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
//            listeners.forEach { it.updateReceived(message = "Branch $id executions is already started!") }
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
        flowEngine.flows.filter { mapping.values.contains(it.id) }.filter { it.id != blockToGo.id }
            .forEach { blockToSkip ->
                blockToSkip.skipped = true
            }
        blockToGo.run(flowEngine)
//        listeners.forEach { it.updateReceived(message = "Executing Branch id '$id', Branching to: $blockIdToBranch, now executing...") }
    }

}

enum class BRANCH_TYPE {
    NORMAL, ROUTER
}



