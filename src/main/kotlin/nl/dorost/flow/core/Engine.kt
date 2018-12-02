package nl.dorost.flow.core

import com.moandjiezana.toml.Toml
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import nl.dorost.flow.MissingFieldException
import nl.dorost.flow.NonUniqueIdException
import nl.dorost.flow.NonUniqueTypeException
import nl.dorost.flow.TypeNotRegisteredException
import nl.dorost.flow.actions.elementaryActions
import nl.dorost.flow.dao.BlocksDaoImpl
import nl.dorost.flow.dto.ActionDto
import nl.dorost.flow.dto.InnerActionDto
import nl.dorost.flow.dto.UserDto
import org.omg.SendingContext.RunTime
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors


class FlowEngine {

    val LOG = KotlinLogging.logger { }

    var flows: MutableList<Block> = mutableListOf()
    var returnValue: MutableMap<String, Any>? = null
    var returnedBlockId: String? = null
    var registeredActions: MutableList<Action> = mutableListOf()
    var secondaryActions: MutableList<Action> = mutableListOf()
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
        val firstLayerBlocks = findFirstLayer(flows)
        if (firstLayerBlocks.isEmpty()) {
            listeners.forEach { it.updateReceived(message = "There is no source block to start the execution, you need to mark at least one `source=true` block!") }
            throw RuntimeException("There is no source block to start the execution, you need to mark at least one `source=true` block!")
        }
        firstLayerBlocks.forEach { block ->
            block.run(this)
        }
    }

    fun registerSecondaryActionsFromDB(blocksDaoImpl: BlocksDaoImpl) {
        this.secondaryActions = blocksDaoImpl.getAllSecondaryActions().map {
            Action().apply {
                type = it.type
                name = it.name
                description = it.description
                innnerBlocks = it.innerBlocks.map {
                    Action().apply {
                        id = it.id
                        type = it.type
                        nextBlocks = it.nextBlocks
                        source = it.source
                        returnAfterExec = it.returnAfterExecution
                    }
                }.toMutableList()
            }
        }.toMutableList()
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
                    registeredActions.plus(secondaryActions).firstOrNull { it.type == block.type }?.let { registeredAction ->
                        block.apply {
                            act = registeredAction.act
                            listeners = this@FlowEngine.listeners
                            innnerBlocks = registeredAction.innnerBlocks
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

    private fun registerNewContainersAsAction(
        containers: List<Container>,
        blocksDaoImpl: BlocksDaoImpl? = null,
        user: UserDto? = null
    ) {

        listeners.forEach { it.updateReceived(message = "${containers.size} containers to register!") }
        // First check elementary actions
        containers.forEach { container ->
            if (container.type in registeredActions.map { it.type }) {
                val msg =
                    "${container.type} already exists as elementary action! Can't register new action with this type."
                listeners.forEach { it.updateReceived(message = msg, type = MessageType.ERROR) }
                throw NonUniqueTypeException(msg)
            }
        }


        containers.forEach { container ->
            // Then check the DB for registered actions
            val innerBlocks =
                getActionMap(this@FlowEngine.flows.first { it.id == container.firstBlock }).toMutableList()

            (innerBlocks.first { it.id == container.firstBlock } as Action).source = true
            (innerBlocks.first { it.id == container.lastBlock } as Action).returnAfterExec = true

            flows.removeIf {
                innerBlocks
                    .map { it.id }
                    .contains(it.id)
            }
            blocksDaoImpl?.let {
                it.getActionByType(container.type!!)?.let { alreadyRegisteredAction ->

                    if (alreadyRegisteredAction.userId == user!!.id) {
                        if (!container.update) {
                            val msg =
                                "This action already exists! consider adding `update=true` or remove the container definition for Container type: ${container.type}"

                            listeners.forEach { it.updateReceived(message = msg, type = MessageType.ERROR) }
                            return
                        } else {
                            TODO("Update the action here!")
                        }
                    } else {
                        listeners.forEach { it.updateReceived(message = "This container type already exists but you don't own it! Use another `type` for your container.") }
                    }

                } ?: run {
                    blocksDaoImpl.registerNewAction(
                        containerToActionDto(
                            container,
                            innerBlocks
                        ).copy(userId = user!!.id)
                    )
                    secondaryActions.add(
                        Action().apply {
                            type = container.type
                            params = container.params?.map { it to "" }?.toMap()?.toMutableMap()
                            innnerBlocks = innerBlocks
                        }
                    )
                    listeners.forEach { it.updateReceived(message = "Container of type ${container.type} registered susscfully by user ${user.id}") }
                }
            } ?: run {
                TODO("If there is no persistence!?")
            }


        }

    }


    fun getActionMap(block: Block): MutableList<Block> {

        val blocks: MutableList<Block> = mutableListOf()
        flows.filter {
            it.dependencies!!.contains(block.id)
        }.forEach {
            when (it) {
                is Action -> if (!it.returnAfterExec) {
                    blocks.add(it)
                    blocks.addAll(getActionMap(it))
                } else {
                    blocks.add(it)
                    return@forEach
                }
                is Branch -> blocks.addAll(getActionMap(it))
                else -> throw RuntimeException()
            }
        }
        return blocks.plus(block).toMutableList()
    }

    fun wire(
        flows: List<Any>, blocksDaoImpl: BlocksDaoImpl? = null,
        user: UserDto? = null
    ) {
        val containers = flows.filter { it is Container }.map { it as Container }
        this.flows = flows.filter { it is Block }.map { it as Block }.toMutableList()
        checkForFields()
        checkForIdUniqeness()
        wireDependencies()
        registerNewContainersAsAction(containers, blocksDaoImpl, user)
        wireProperActsToBlocks()
    }

    private fun checkForFields() {
        flows.filter { it is Branch }.map { it as Branch }.forEach {
            if (it.on == null) {
                val msg = "Branch id ${it.id} doesn't have on attribute!"
                listeners.forEach { it.updateReceived(message = msg, type = MessageType.ERROR) }
                throw RuntimeException(msg)
            }
        }
        flows.filter { it is Action }.map { it as Action }.firstOrNull { it.source }
            ?: run {
                if (flows.size!=0) {
                    val msg = "There should be at least one source!"
                    listeners.forEach { it.updateReceived(message = msg, type = MessageType.ERROR) }
                    throw RuntimeException()
                }
            }
    }

    private fun findFirstLayer(flows: MutableList<Block>): List<Block> =
        flows.filter { it is Action }.map { it as Action }.filter { it.source }

    fun parseToBlocks(toml: Toml): MutableList<Any> {
        val actions = parseActions(toml)
        val branches = parseBranches(toml)
        val containers = parseContainers(toml)
        return actions.plus(branches).plus(containers).toMutableList()
    }

    private fun parseContainers(toml: Toml): List<Container> = toml.toMap()["container"]?.let {
        (it as List<HashMap<String, Any>>).map {
            Container().apply {
                type = it["type"] as? String ?: throw MissingFieldException("Type should be defined!")
                id = it["id"]!! as String
                firstBlock = it["first"] as String
                lastBlock = it["last"] as String
                description = it["description"]?.let { it as String }
                update = (it["update"] as? Boolean) ?: false
                params = it["params"] as? List<String>
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
                Action().apply {
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
            }
        } ?: listOf()


    fun readFlowsFromDir(path: String) =
        Files.walk(Paths.get(path)).filter { Files.isRegularFile(it) }.collect(Collectors.toList()).flatMap { path: Path ->
            val toml = Toml().read(path.toFile())
            val blocks = this.parseToBlocks(toml)
            val containers = this.parseContainers(toml)
            blocks.plus(containers)
        }


    fun tomlToBlocks(tomlText: String): MutableList<Any> {
        val toml: Toml?
        try {
            toml = Toml().read(tomlText)
        } catch (e: Exception) {
            val errorMessage = "TOML could not be parsed correctly! ${e.localizedMessage}"
            listeners.forEach { it.updateReceived(message = errorMessage) }
            throw RuntimeException(errorMessage)
        }
        val blocks: MutableList<Any>
        try {
            blocks = this.parseToBlocks(toml)
        } catch (e: Exception) {
            val errorMessage = "Error on parsing to blocks! ${e.localizedMessage}"
            listeners.forEach { it.updateReceived(message = errorMessage) }
            throw RuntimeException(errorMessage)
        }
        return blocks
    }

    fun blocksToDigraph(): String {
        var digraph = "digraph {\n"
        digraph += "node [rx=5 ry=5 labelStyle=\"font: 300 14px 'Helvetica Neue', Helvetica\"]\n"
        digraph += "edge [labelStyle=\"font: 300 14px 'Helvetica Neue', Helvetica\"]\n"

        val actionInnerHtml = "<label style='color:rgb(0,0,0);'> Action: <b>%s</b>  </label>" +
                "<button class='badge badge-info badge-pill' id='%s-edit'>edit</button>" +
                "<button class='badge badge-danger badge-pill' id='%s-remove'>remove</button>"

        val sourceActionInnerHtml =
            "<img src='http://www.icons101.com/icon_ico/id_81542/Start.ico' alt='Source Action' height='18' width='18'>" +
                    "<label style='color:rgb(0,0,0);'> Action: <b>%s</b>  </label>" +
                    "<button class='badge badge-info badge-pill' id='%s-edit'>edit</button>" +
                    "<button class='badge badge-danger badge-pill' id='%s-remove'>remove</button>"

        val branchInnerHtml = "<label style='color:rgb(0,0,0);'> Branch: <b>%s</b>  </label>" +
                "<label style='color:rgb(0,0,0);'> on: <b>%s</b>  </label>" +
                "<button class='badge badge-info badge-pill'  id='%s-edit'>edit</button>" +
                "<button class='badge badge-danger badge-pill id='%s-remove'>remove</button>"

        val normalEdgeHtml =
            "\"%s\" -> \"%s\" [style=\"stroke: #404040; stroke-width: 3px;\" arrowheadStyle=\"fill: #404040\"];\n"
        val branchEdgeHtml =
            "\"%s\" -> \"%s\" [label=\"%s\" labelStyle=\"fill: #55f; font-weight: bold;\" style=\"stroke: #404040; stroke-width: 3px;\" arrowheadStyle=\"fill: #404040\"];\n"

        val normalNode = "\"%s\" [labelType=\"html\" label=\"%s\" style=\"fill: #E0E0E0;\"];\n"
        val branchNode = "\"%s\" [labelType=\"html\" label=\"%s\" style=\"fill: #FFCCCC;\"];\n"

        // add nodes
        flows.filter { it is Action }.map { it as Action }.forEach { action ->
            digraph += String.format(
                normalNode,
                action.id,
                String.format(
                    if (action.source) sourceActionInnerHtml else actionInnerHtml,
                    action.name,
                    action.id,
                    action.id
                )
            )
        }

        // add branches
        flows.filter { it is Branch }.map { it as Branch }.forEach { block ->
            digraph += String.format(
                branchNode,
                block.id,
                String.format(
                    branchInnerHtml,
                    block.name,
                    block.on,
                    block.id,
                    block.id
                )
            )
        }


        // add edges
        flows.filter { it is Action }.map { it as Action }.flatMap { block ->
            block.nextBlocks.map { Pair(block.id, it) }
        }.forEach { edge ->
            digraph += String.format(normalEdgeHtml, edge.first, edge.second)
        }

        // add branch edges
        flows.filter { it is Branch }.map { it as Branch }.flatMap { branch ->
            branch.mapping.map { Pair(branch.id, it) }
        }.forEach { mapping ->
            digraph += String.format(branchEdgeHtml, mapping.first, mapping.second.value, mapping.second.key)
        }

        digraph += "}"
        return digraph
    }

    fun blocksToToml(blocks: MutableList<Block>): String {
        var toml = "#This Flow is generated by Sushi Backend.\n" +
                "# ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME)}\n"

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

    fun await(): Boolean {
        var count: Int
        do {
            count = flows.count { it.started } + flows.count { it.skipped }
            if (flows.filter { it.started }.any { it.output?.isCancelled == true }) {
                listeners.forEach {
                    it.updateReceived(
                        message = "Execution Failed to Complete!",
                        type = MessageType.ERROR
                    )
                }
                return false
            }
        } while (count != flows.size || flows.filter { it.started }.any { it.output?.isCompleted != true })
        return true
    }

    private fun containerToActionDto(container: Container, innerBlocks: List<Block>): ActionDto {
        return ActionDto(
            type = container.type ?: throw MissingFieldException("Type field should be defined for container!"),
            name = container.name,
            description = container.description,
            innerBlocks = innerBlocks.map {
                InnerActionDto(
                    type = it.type!!,
                    id = it.id!!,
                    nextBlocks = (it as Action).nextBlocks,
                    source = it.source,
                    returnAfterExecution = it.returnAfterExec
                )
            },
            params = container.params?: listOf()

        )
    }


}



