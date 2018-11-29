package nl.dorost.flow.core

import com.moandjiezana.toml.Toml
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import nl.dorost.flow.NonUniqueIdException
import nl.dorost.flow.NonUniqueTypeException
import nl.dorost.flow.TypeNotRegisteredException
import nl.dorost.flow.actions.elementaryActions
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors


class FlowEngine {

    val LOG = KotlinLogging.logger { }

    var flows: MutableList<Block> = mutableListOf()
    var returnValue: Deferred<MutableMap<String, Any>>? = null
    var returnedBlockId: String? = null
    var registeredActions: MutableList<Action> = mutableListOf()
    var listeners: List<BlockListener> = mutableListOf()

    init {
        registerActions(elementaryActions)
    }

    fun registerActions(blocks: MutableList<Action>) {
        registeredActions.addAll(blocks)
    }

    fun registerListeners(listeners: List<BlockListener>) {
        this.listeners = listeners
    }

    fun executeFlow(input: MutableMap<String, Any> = mutableMapOf()) {
        LOG.info("Starting the execution!")
        val firstLayerBlocks = findFirstLayer(flows)
        if (firstLayerBlocks.isEmpty()) {
            LOG.error("There is no source block to start the execution, you need to mark at least one `source=true` block!")
            throw RuntimeException("There is no source block to start the execution, you need to mark at least one `source=true` block!")
        }
        firstLayerBlocks.forEach { block ->
            block.run(this)
        }
    }


    private fun wireDependencies() {
        flows.forEach { block ->
            block.dependencies = getDependentBlocks(block).map { it.id!! }.toMutableList()
        }
    }

    private fun wireProperActsToBlocks() {

        this.flows = flows.map { block ->
            when (block) {
                is Action -> {
                    registeredActions.firstOrNull { it.type == block.type }?.let { registeredAction ->
                        block.apply {
                            act = registeredAction.act
                            listeners = this@FlowEngine.listeners
                        }
                    } ?: throw TypeNotRegisteredException("Type ${block.type} is not a registered action!")
                }
                is Branch -> block
                else -> throw RuntimeException("Not expecting this type of block!")
            }
        }.toMutableList()
    }


    private fun checkForIdUniqeness() {
        val duplicates = flows.map { it.id }
            .groupingBy { it }
            .eachCount()
            .filter { it.value > 1 }
            .map { it.key }
        if (duplicates.isNotEmpty())
            throw NonUniqueIdException("All ids must be unique! Duplicate id: ${duplicates.first()}.")
    }

    private fun registerNewContainersAsAction(containers: List<Container>) {
        containers.forEach { container ->
            if (container.type in registeredActions.map { it.type })
                throw NonUniqueTypeException("${container.type} already exists! Can't register new action with this type.")
        }



        containers.forEach { container ->
            val firstBlock = flows.first { it.id == container.firstBlock }.apply { (this as Action).source = true }
            val lastBlock =
                flows.first { it.id == container.lastBlock }.apply { (this as Action).returnAfterExec = true }


            val innerBlocksBlocks = getActionMap(firstBlock)
            registeredActions.add(
                Action().apply {
                    type = container.type
                    params = container.params
                    innnerBlocks = innerBlocksBlocks
                }
            )

            flows.removeIf { innerBlocksBlocks.map { it.id }.contains(it.id) }

        }

    }


    fun getActionMap(block: Block): MutableList<Block> {

        val blocks: MutableList<Block> = mutableListOf()
        flows.filter {
            when (it) {
                is Action -> it.nextBlocks.contains(block.id)
                is Branch -> it.mapping.values.contains(block.id)
                else -> throw RuntimeException()
            }
        }.forEach {
            when (it) {
                is Action -> if (!it.returnAfterExec)
                    blocks.addAll(getActionMap(it))
                else
                    return@forEach
                is Branch -> blocks.addAll(getActionMap(it))
                else -> throw RuntimeException()
            }
        }
        return blocks
    }

    fun wire(flows: List<Any>) {
        this.flows = flows.filter { it is Block }.map { it as Block }.toMutableList()
        checkForIdUniqeness()
        registerNewContainersAsAction(flows.filter { it is Container }.map { it as Container })
        wireDependencies()
        wireProperActsToBlocks()
    }

    private fun findFirstLayer(flows: MutableList<Block>): List<Block> =
        flows.filter { it is Action }.map { it as Action }.filter { it.source }

    fun parseToBlocks(toml: Toml): MutableList<Block> {
        val actions = parseActions(toml)
        val branches = parseBranches(toml)
        return actions.plus(branches).toMutableList()
    }

    private fun parseContainers(toml: Toml): List<Container> = toml.toMap()["container"]?.let {
        (it as List<HashMap<String, Any>>).map {
            Container().apply {
                type = it.getOrDefault("type", "normal") as String
                id = it["id"]!! as String
                firstBlock = it["first"] as String
                lastBlock = it["last"] as String
                description = it["description"]?.let { it as String }
            }
        }
    } ?: listOf()

    private fun parseBranches(toml: Toml): List<Branch> = toml.toMap()["branch"]?.let {
        (it as List<HashMap<String, Any>>).map { block: HashMap<String, Any> ->
            Branch().apply {
                name = block["name"]!! as String
                type = block.getOrDefault("type", BRANCH_TYPE.NORMAL.toString()) as String
                id = block["id"]!! as String
                mapping = block["mapping"]?.let { it as MutableMap<String, String> } ?: mutableMapOf()
                on = block["on"]!! as String
                description = block["description"]?.let { it as String } ?: ""
            }
        }
    } ?: listOf()

    fun getDependentBlocks(block: Block): MutableList<Block> = this.flows.filter { it ->
        when (it) {
            is Action -> it.nextBlocks.contains(block.id)
            is Branch -> it.mapping.values.contains(block.id)
            else -> throw RuntimeException("Not supported Block!")
        }
    }.toMutableList()


    private fun parseActions(toml: Toml): List<Block> =
        toml.toMap()["action"]?.let {
            (it as List<HashMap<String, Any>>).map { block: HashMap<String, Any> ->
                registeredActions.firstOrNull { it.type == block["type"] }?.let { action ->
                    action.apply {
                        name = block["name"]!! as String
                        type = block["type"]!! as String
                        returnAfterExec = block.getOrDefault("returnAfter", false) as Boolean
                        id = block["id"]!! as String
                        params = block.getOrDefault(
                            "params",
                            mutableMapOf<String, String>()
                        ) as MutableMap<String, String>
                        nextBlocks = block.getOrDefault("next", mutableListOf<String>()) as MutableList<String>
                        source = block["source"] as? Boolean ?: false
                        description = block["description"]?.let { it as String } ?: ""
                    }
                } ?: throw TypeNotRegisteredException("'${block["type"]}' type is not registered!")
            }
        } ?: listOf()

    fun readFlowsFromDir(path: String) =
        Files.walk(Paths.get(path)).filter { Files.isRegularFile(it) }.collect(Collectors.toList()).flatMap { path: Path ->
            val toml = Toml().read(path.toFile())
            val blocks = this.parseToBlocks(toml)
            val containers = this.parseContainers(toml)
            blocks.plus(containers)
        }


    fun tomlToBlocks(tomlText: String): MutableList<Block> {
        val toml: Toml?
        try {
            toml = Toml().read(tomlText)
        } catch (e: Exception) {
            val errorMessage = "TOML could not be parsed correctly! ${e.localizedMessage}"
            LOG.error(errorMessage)
            throw RuntimeException(errorMessage)
        }
        val blocks: MutableList<Block>
        try {
            blocks = this.parseToBlocks(toml)
        } catch (e: Exception) {
            val errorMessage = "Error on parsing to blocks! ${e.localizedMessage}"
            LOG.error(errorMessage)
            throw RuntimeException(errorMessage)
        }
        return blocks
    }

    fun blocksToDigraph(blocks: MutableList<Block>): String {
        var digraph = "digraph {\n"
        digraph += "node [rx=5 ry=5 labelStyle=\"font: 300 14px 'Helvetica Neue', Helvetica\"]\n"
        digraph += "edge [labelStyle=\"font: 300 14px 'Helvetica Neue', Helvetica\"]\n"

        val actionInnerHtml = "<label style='color:rgb(0,0,0);'> Action: <b>%s</b>  </label>" +
                "<button class='badge badge-info badge-pill' id='%s-edit'>edit</button>" +
                "<button class='badge badge-danger badge-pill' id='%s-remove'>remove</button>"

        val branchInnerHtml = "<label style='color:rgb(0,0,0);'> Branch: <b>%s</b>  </label>" +
                "<label style='color:rgb(0,0,0);'> on: <b>%s</b>  </label>" +
                "<button class='badge badge-info badge-pill'  id='%s-edit'>edit</button>" +
                "<button class='badge badge-danger badge-pill id='%s-remove''>remove</button>"

        val normalEdgeHtml =
            "\"%s\" -> \"%s\" [style=\"stroke: #404040; stroke-width: 3px;\" arrowheadStyle=\"fill: #404040\"];\n"
        val branchEdgeHtml =
            "\"%s\" -> \"%s\" [label=\"%s\" labelStyle=\"fill: #55f; font-weight: bold;\" style=\"stroke: #404040; stroke-width: 3px;\" arrowheadStyle=\"fill: #404040\"];\n"

        val normalNode = "\"%s\" [labelType=\"html\" label=\"%s\" style=\"fill: #E0E0E0;\"];\n"
        val branchNode = "\"%s\" [labelType=\"html\" label=\"%s\" style=\"fill: #FFCCCC;\"];\n"

        // add nodes
        blocks.filter { it is Action }.forEach { block ->
            digraph += String.format(
                normalNode,
                block.id,
                String.format(actionInnerHtml, block.name, block.id, block.id)
            )
        }

        // add branches
        blocks.filter { it is Branch }.map { it as Branch }.forEach { block ->
            digraph += String.format(
                branchNode,
                block.id,
                String.format(branchInnerHtml, block.name, block.on, block.id, block.id)
            )
        }


        // add edges
        blocks.filter { it is Action }.map { it as Action }.flatMap { block ->
            block.nextBlocks.map { Pair(block.id, it) }
        }.forEach { edge ->
            digraph += String.format(normalEdgeHtml, edge.first, edge.second)
        }

        // add branch edges
        blocks.filter { it is Branch }.map { it as Branch }.flatMap { branch ->
            branch.mapping.map { Pair(branch.id, it) }
        }.forEach { mapping ->
            digraph += String.format(branchEdgeHtml, mapping.first, mapping.second.value, mapping.second.key)
        }

        digraph += "}"
        return digraph
    }

    fun blocksToToml(blocks: MutableList<Block>): String {
        var toml = "[flow]\n" +
                "name = \"Generated Flow\"\n"

        blocks.forEach { block ->
            when (block) {
                is Action -> {
                    toml += "[[action]]\n" +
                            "   name = \"${block.name}\"\n" +
                            "   id = \"${block.id}\"\n" +
                            "   type = \"${block.type}\"\n"
                    if (block.source)
                        toml += "   source = true\n"

                    if (block.nextBlocks.size > 0)
                        toml += block.nextBlocks.map { "\"$it\"" }
                            .joinToString(separator = ",", prefix = "   next = [", postfix = "]\n")

                    if (block.params?.size!! > 0)
                        toml += block.params?.map { "        ${it.key} = \"${it.value}\"" }
                            ?.joinToString(separator = "\n", postfix = "\n", prefix = "   [action.params]\n")
                }
                is Branch -> {
                    toml += "[[branch]]\n" +
                            "   name = \"${block.name}\"\n" +
                            "   id = \"${block.id}\"\n" +
                            "   on = \"${block.on}\"\n"
                    if (block.mapping.size > 0)
                        toml += block.mapping.entries.map { "        ${it.key} = \"${it.value}\"" }
                            .joinToString(separator = "\n", postfix = "\n", prefix = "   [branch.mapping]\n")

                }
            }
        }

        return toml

    }

    fun await(){
        GlobalScope.launch {
            this@FlowEngine.flows.forEach { it.output?.await() }
        }
    }

}

