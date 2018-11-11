package nl.dorost.flow.core

import com.moandjiezana.toml.Toml
import nl.dorost.flow.InvalidNextIdException
import nl.dorost.flow.NonUniqueIdException
import nl.dorost.flow.TypeNotRegisteredException
import nl.dorost.flow.actions.elementaryActions
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors


class FlowEngine {

    var flows: MutableList<Block> = mutableListOf()
    var returnValue: MutableMap<String, Any> = mutableMapOf()
    var returnedBlockId: String? = null
    var registeredActions: Map<String, (action: Action) -> Map<String, Any>> = mapOf()

    init {
        registerActions(elementaryActions)
    }

    fun registerActions(blocks: Map<String, (action: Action) -> Map<String, Any>>) {
        registeredActions = registeredActions.plus(blocks)
    }

    fun executeFlow(input: MutableMap<String, Any> = mutableMapOf()) {
        val firstLayerBlocks = findFirstLayer(flows)
        if (firstLayerBlocks.isEmpty())
            LOG.warn("There is no source block to start the execution, you need to mark at least one `source=true` block!")
        firstLayerBlocks.forEach { block ->
            block.input = input
            block.run(this)
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
        }.forEach { nextBlock ->
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

//    private fun findFirstLayer(flows: MutableList<Block>): List<Block> {
//        val allIds = flows.map { it.id }
//        val secondLayerBlocks = flows.filter { it is Action }.map { it as Action }.flatMap { it.nextBlocks }.plus(
//            flows.filter { it is Branch }.map { it as Branch }.flatMap { it.mapping.values }
//        )
//            .distinct()
//        val firstOfContainers = flows.filter { it is Container }.map { (it as Container).firstBlock }
//        val fistLayerIds = allIds.subtract(secondLayerBlocks).subtract(firstOfContainers)
//        return flows.filter { it.id in fistLayerIds }
//    }

    private fun findFirstLayer(flows: MutableList<Block>): List<Block> = flows.filter { it.source }

    fun parseToBlocks(toml: Toml): MutableList<Block> {
        val containers = parseContainers(toml)
        val actions = parseActions(toml)
        val branches = parseBranches(toml)
        return containers.plus(actions).plus(branches).toMutableList()
    }

    private fun parseContainers(toml: Toml): List<Block> {
        return (toml.toMap().getOrDefault(
            "container",
            listOf<HashMap<String, Any>>()
        ) as List<HashMap<String, Any>>).map {
            Container(
                name = it["name"]!! as String,
                type = it["type"]!! as String,
                id = it["id"]!! as String,
                params = it.getOrDefault("params", mutableMapOf<String, String>()) as HashMap<String, String>,
                firstBlock = it["first"] as String,
                lastBlock = it["last"] as String,
                source = it.getOrDefault("source", false) as Boolean
            ) as Block
        }
    }

    private fun parseBranches(toml: Toml): List<Branch> {
        return (toml.toMap().getOrDefault("branch", listOf<HashMap<String, Any>>()) as List<HashMap<String, Any>>).map {
            Branch(
                name = it["name"]!! as String,
                type = it.getOrDefault("type", BRANCH_TYPE.NORMAL.toString()) as String,
                id = it["id"]!! as String,
                params = it["params"] as HashMap<String, String>,
                mapping = it.getOrDefault("mapping", mutableMapOf<String, String>()) as HashMap<String, String>,
                source = it.getOrDefault("source", false) as Boolean
            )
        }
    }

    private fun parseActions(toml: Toml): List<Block> {
        return (toml.toMap()["action"] as List<HashMap<String, Any>>).map {
            Action(
                name = it["name"]!! as String,
                type = it["type"]!! as String,
                returnAfterExec = it.getOrDefault("returnAfter", false) as Boolean,
                id = it["id"]!! as String,
                params = it.getOrDefault("params", mutableMapOf<String, String>()) as MutableMap<String, String>,
                nextBlocks = it.getOrDefault("next", mutableListOf<String>()) as MutableList<String>,
                source = it.getOrDefault("source", false) as Boolean
            ) as Block
        }
    }

    fun readFlowsFromDir(path: String) =
        Files.walk(Paths.get(path)).filter { Files.isRegularFile(it) }.collect(Collectors.toList()).flatMap {
            val toml = Toml().read(it.toFile())
            val blocks = this.parseToBlocks(toml)
            blocks
        }

}