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
    var registeredActions: MutableMap<String, (action: Action) -> Map<String, Any>> = mutableMapOf()
    var prefixes: MutableSet<String> = mutableSetOf()

    init {
        registerActions(elementaryActions)
    }

    fun registerActions(blocks: MutableMap<String, (action: Action) -> Map<String, Any>>) {
        registeredActions.putAll(blocks)
    }

    fun executeFlow(input: MutableMap<String, Any> = mutableMapOf()) {
        LOG.info("Starting the execution!")
        val firstLayerBlocks = findFirstLayer(flows)
        if (firstLayerBlocks.isEmpty())
            LOG.warn("There is no source block to start the execution, you need to mark at least one `source=true` block!")
        firstLayerBlocks.forEach { block ->
            block.input = input
            block.run(this)
        }
    }

    private fun verify(flows: List<Block>) {
        checkForIdUniqeness(flows)
        registerContainers(flows)
        checkIfTypesRegistered(flows)
        checkIdPrefixes(flows)
        wireProperActsToBlocks(flows)
    }

    private fun wireProperActsToBlocks(flows: List<Block>) {
        flows.filter { it is Action }.map { it as Action }.forEach { currentBlock ->
            currentBlock.act = registeredActions[currentBlock.type]
        }
    }

    private fun checkIdPrefixes(flows: List<Block>) {
        // Check if next block ids are valid
        val correctionsList: MutableMap<String, String> = mutableMapOf()
        flows.filter { it is Action }.map { it as Action }.flatMap { action ->
            action.nextBlocks.map { action.id to it }
        }.forEach { parentAndChild ->
            val parent = parentAndChild.first
            val child = parentAndChild.second
            if (!flows.map { it.id }.contains(child)) {
                val pref = prefixes.firstOrNull { pref ->
                    flows.map { it.id }.contains("$pref$child")
                } ?: throw InvalidNextIdException("Invalid next id: $child")
                correctionsList[parent!!] = pref
            }
        }

        // Corrections applied to next blocks
        flows.filter { it.id in correctionsList.keys }.forEach { action ->
            val updatedIds = (action as Action).nextBlocks.map {
                val newID = "${correctionsList[action.id]}$it"
                LOG.warn("Changing next id: $it to $newID for parent ${action.id}")
                newID
            }
            action.nextBlocks = updatedIds.toMutableList()
        }

        // Check if container first and last blocks need corrections
        flows.filter { it is Container }.map { it as Container }
            .forEach { container ->
                if (!flows.map { it.id }.contains(container.firstBlock)) {
                    val pref = prefixes.firstOrNull { pref ->
                        flows.map { it.id }.contains("$pref${container.firstBlock}")
                    } ?: throw InvalidNextIdException("Invalid first block id: ${container.firstBlock}")
                    LOG.warn("Changing first block id to $pref${container.firstBlock} for ${container.id}")
                    container.firstBlock = "$pref${container.firstBlock}"
                }

                if (!flows.map { it.id }.contains(container.lastBlock)) {
                    val pref = prefixes.firstOrNull { pref ->
                        flows.map { it.id }.contains("$pref${container.lastBlock}")
                    } ?: throw InvalidNextIdException("Invalid last block id: ${container.lastBlock}")
                    LOG.warn("Changing last block id to $pref${container.lastBlock} for ${container.id}")
                    container.lastBlock = "$pref${container.lastBlock}"
                }
            }

        // Check for Branches
        flows.filter { it is Branch }.map { it as Branch }
            .forEach { branch ->
                branch.mapping.entries.forEach { (nextMappingKey, nextMappingValue) ->
                    if (!flows.map { it.id }.contains(nextMappingValue)) {
                        val pref = prefixes.firstOrNull { pref ->
                            flows.map { it.id }.contains("$pref${nextMappingValue}")
                        } ?: throw InvalidNextIdException("Invalid mapping id: ${nextMappingValue}")
                        LOG.warn("Changing mapping value to $pref${nextMappingValue} for ${branch.id}")
                        branch.mapping[nextMappingKey] = "$pref${nextMappingValue}"
                    }
                }

            }
    }

    private fun checkIfTypesRegistered(flows: List<Block>) {
        flows.filter { it is Action }.forEach {
            if (it.type !in registeredActions.keys)
                throw TypeNotRegisteredException("'${it.type}' type is not a registered Block!")
        }
    }

    private fun checkForIdUniqeness(flows: List<Block>) {
        val duplicates = flows.map { it.id }
            .groupingBy { it }
            .eachCount()
            .filter { it.value > 1 }
            .map { it.key }
        if (duplicates.isNotEmpty())
            throw NonUniqueIdException("All ids must be unique! Duplicate id: ${duplicates.first()}.")
    }

    private fun registerContainers(containers: List<Block>) {
        containers.filter { it is Container }.map { it as Container }.forEach { container ->
            registeredActions[container.type] = { action: Action ->
                container.run(this)
            }
        }

    }

    fun wire(flows: List<Block>) {
        this.flows = flows as MutableList<Block>
        verify(flows)
    }

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
                type = it.getOrDefault("type", "normal") as String,
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
            val flowInfo = toml.toMap().getOrDefault("flow", mapOf("id_prefix" to "")) as HashMap<String, String>
            val prefix = flowInfo.getOrDefault("id_prefix", "")
            prefixes.add(prefix)
            addPrefixToIds(blocks, prefix)
            blocks
        }

    private fun addPrefixToIds(blocks: MutableList<Block>, prefix: String) {
        blocks.forEach { it.id = "${prefix}${it.id}" }
    }


    fun tomlToDigraph(tomlText: String): String {
        val toml: Toml?
        try {
            toml = Toml().read(tomlText)
        } catch (e: Exception) {
            val errorMessage = "TOML could not be parsed correctly! ${e.localizedMessage}"
            LOG.error(errorMessage)
            return errorMessage
        }
        val blocks: MutableList<Block>
        try {
            blocks = this.parseToBlocks(toml)
        } catch (e: Exception) {
            val errorMessage = "Error on parising to blocks! ${e.localizedMessage}"
            LOG.error(errorMessage)
            return errorMessage
        }
        var digraph = "digraph {\n"
        digraph += "node [rx=5 ry=5 labelStyle=\"font: 300 14px 'Helvetica Neue', Helvetica\"]\n"
        digraph += "edge [labelStyle=\"font: 300 14px 'Helvetica Neue', Helvetica\"]\n"

        val actionInnerHtml = "<label style='color:rgb(0,0,0);'> Action: <b>%s</b>  </label>" +
                "<br><button class='badge badge-danger badge-pill'>edit</button>" +
                "<button class='badge badge-danger badge-pill'>remove</button>"

        val branchInnerHtml = "<label style='color:rgb(0,0,0);'> Branch: <b>%s</b>  </label>" +
                "<br><button class='badge badge-danger badge-pill'>edit</button>" +
                "<button class='badge badge-danger badge-pill'>remove</button>"

        val normalEdgeHtml =
            "\"%s\" -> \"%s\" [style=\"stroke: #404040; stroke-width: 3px;\" arrowheadStyle=\"fill: #404040\"];\n"
        val branchEdgeHtml =
            "\"%s\" -> \"%s\" [label=\"%s\" labelStyle=\"fill: #55f; font-weight: bold;\" style=\"stroke: #404040; stroke-width: 3px;\" arrowheadStyle=\"fill: #404040\"];\n"

        val normalNode = "\"%s\" [labelType=\"html\" label=\"%s\" style=\"fill: #E0E0E0;\"];\n"
        val branchNode = "\"%s\" [labelType=\"html\" label=\"%s\" style=\"fill: #FFCCCC;\"];\n"

        // add nodes
        blocks.filter { it is Action }.forEach { block ->
            val name = block.id
            digraph += String.format(normalNode, name, String.format(actionInnerHtml, name))
        }

        // add branches
        blocks.filter { it is Branch }.forEach { block ->
            val name = block.id
            digraph += String.format(branchNode, name, String.format(branchInnerHtml, name))
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

}

