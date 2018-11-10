package nl.dorost.flow

import com.moandjiezana.toml.Toml
import nl.dorost.flow.actions.elementaryActions
import org.slf4j.LoggerFactory

val LOG = LoggerFactory.getLogger("FlowEngine")

abstract class Block(
    open val name: String,
    open val id: String?,
    open val type: String,
    open var input: MutableMap<String, Any> = mutableMapOf(),
    open val params: MutableMap<String, String> = mutableMapOf()
) {
    abstract fun run(flows: List<Block>)
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

    override fun run(flows: List<Block>) {
        LOG.info("Executing Container id '$id', first is $firstBlock")
        flows.first { it.id == firstBlock }.run(flows)
    }
}

data class Action(
    var act: ((action: Action) -> Map<String, Any>)? = null,
    val nextBlocks: MutableList<String> = mutableListOf(),
    override val name: String,
    override val id: String? = null,
    override val type: String,
    override var input: MutableMap<String, Any> = mutableMapOf(),
    override val params: MutableMap<String, String> = mutableMapOf()
) : Block(name, id, type, input, params) {

    override fun run(flows: List<Block>) {
        val output = this.act!!.invoke(this)
        LOG.info("Executed Action id '$id', Type: '$type', Output was: $output")

        nextBlocks.map { nextId ->
            flows.first { it.id == nextId }
        }.forEach { nextBlock ->
            nextBlock.input = output.toMutableMap()
            nextBlock.run(flows)
        }
    }
}

data class Branch(
    val mapping: HashMap<String, String>,
    override val name: String,
    override val id: String? = null,
    override val type: String,
    override var input: MutableMap<String, Any> = mutableMapOf(),
    override val params: MutableMap<String, String>
) : Block(name, id, type, input, params) {
    override fun run(flows: List<Block>) {
        val variableToLook =
            params.get("var-name") ?: throw ExpectedParamNotPresentException("Parameter var-name is not specified!")
        val valueToLook = mapping.get(input.get(variableToLook))
            ?: throw MissingMappingValueException("No mapping specified for the value")

        LOG.info("Executing Branch id '$id', Inputs: ${this.input}, Params: ${this.params}, Branching to: $valueToLook")
        flows.first { it.id == valueToLook }.run(flows)
    }
}


class FlowEngine {

    var flows: MutableList<Block> = mutableListOf()

    var registeredActions: Map<String, (action: Action) -> Map<String, Any>> = mapOf()

    init {
        registerActions(elementaryActions)
    }

    fun registerActions(blocks: Map<String, (action: Action) -> Map<String, Any>>) {
        registeredActions = registeredActions.plus(blocks)
    }

    fun executeFlow(input: Map<String, Any> = mapOf()) {
        val firstLayerBlocks = findFirstLayer(flows)
        firstLayerBlocks.forEach { block ->
            block.run(flows)
        }
    }

    private fun verify(flows: List<Block>) {
        // unique ids
        val duplicates = flows.map { it.id }
            .groupingBy { it }
            .eachCount()
            .filter { it.value > 1 }
            .map { it.key }
        if (duplicates.isNotEmpty())
            throw NonUniqueIdException("All ids must be unique! Duplicate id: ${duplicates.first()}.")

        // Check if the type is already registered
        flows.filter { it is Action }.forEach {
            if (it.type !in registeredActions.keys)
                throw TypeNotRegisteredException("'${it.type}' type is not a registered Block!")
        }


        // Check if next block ids are valid
        flows.filter { it is Action }.map { it as Action }.flatMap {
            it.nextBlocks
        }.forEach {nextBlock ->
            if (!flows.map { it.id }.contains(nextBlock))
                throw InvalidNextIdException("Invalid next id: ${nextBlock}")
        }


        // Wire registered blocks to flows
        flows.filter { it is Action }.map { it as Action }.forEach { currentBlock ->
            currentBlock.act = registeredActions[currentBlock.type]
        }

    }

    fun wire(flows: List<Block>) {
        this.flows = flows as MutableList<Block>
        verify(flows)
    }

    private fun findFirstLayer(flows: MutableList<Block>): List<Block> {
        val allIds = flows.map { it.id }
        val secondLayerBlocks = flows.filter { it is Action }.map { it as Action }.flatMap { it.nextBlocks }.plus(
            flows.filter { it is Branch }.map { it as Branch }.flatMap { it.mapping.values }
        )
            .distinct()
        val firstOfContainers = flows.filter { it is Container }.map { (it as Container).firstBlock }
        val fistLayerIds = allIds.subtract(secondLayerBlocks).subtract(firstOfContainers)
        return flows.filter { it.id in fistLayerIds }
    }

    fun parseToBlocks(toml: Toml): MutableList<Block> {
        val containers = (toml.toMap()["container"] as List<HashMap<String, Any>>).map {
            Container(
                name = it["name"]!! as String,
                type = it["type"]!! as String,
                id = it["id"]!! as String,
                params = it["params"] as HashMap<String, String>,
                firstBlock = it["first"] as String,
                lastBlock = it["last"] as String
            ) as Block
        }

        val actions = (toml.toMap()["action"] as List<HashMap<String, Any>>).map{
            Action(
                name = it["name"]!! as String,
                type = it["type"]!! as String,
                id = it["id"]!! as String,
                params = it.getOrDefault("params", mapOf<String, String>())  as MutableMap<String, String>,
                nextBlocks = it.getOrDefault("next", mutableListOf<String>()) as MutableList<String>
            ) as Block
        }

        val branches = (toml.toMap()["branch"] as List<HashMap<String, Any>>).map {
            Branch(
                name = it["name"]!! as String,
                type = it["type"]!! as String,
                id = it["id"]!! as String,
                params = it["params"] as HashMap<String, String>,
                mapping = it["mapping"] as HashMap<String, String>
            )
        }
        return containers.plus(actions).plus(branches).toMutableList()
    }

}


class NonUniqueIdException(msg: String) : RuntimeException(msg)
class InvalidNextIdException(msg: String) : RuntimeException(msg)
class TypeNotRegisteredException(msg: String) : RuntimeException(msg)
class ExpectedParamNotPresentException(msg: String) : RuntimeException(msg)
class MissingMappingValueException(msg: String) : RuntimeException(msg)