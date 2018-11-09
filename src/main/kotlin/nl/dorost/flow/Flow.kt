package nl.dorost.flow

import nl.dorost.flow.actions.elementaryBlocks


abstract class Block(
    open val name: String,
    open val id: String?,
    open val type: String,
    open val params: MutableMap<String, String> = mutableMapOf(),
    open val nextBlocks: MutableList<String> = mutableListOf()
)

data class Container(
    var firstBlock: String? = null,
    var lastBlock: String? = null,
    override  val name: String,
    override val id: String? = null,
    override val type: String,
    override val params: MutableMap<String, String> = mutableMapOf(),
    override val nextBlocks: MutableList<String> = mutableListOf()
): Block(name, id, type, params, nextBlocks)

data class Action(
    var act: ((input: Map<String, Any>) -> Map<String, Any>)? = null,
    override  val name: String,
    override  val id: String? = null,
    override  val type: String,
    override  val params: MutableMap<String, String> = mutableMapOf(),
    override  val nextBlocks: MutableList<String> = mutableListOf()
): Block(name, id, type, params, nextBlocks)

data class Branch(
    val mapping: HashMap<String, String>,
    override  val name: String,
    override  val id: String? = null,
    override  val type: String,
    override  val params: MutableMap<String, String>,
    override  val nextBlocks: MutableList<String>
): Block(name, id, type, params, nextBlocks)



class FlowEngine{

    var flows: MutableList<Block> = mutableListOf()

    var registeredBlocks: MutableList<Block> = mutableListOf()

    init {
        registerBlocks(elementaryBlocks)
    }

    fun registerBlocks(blocks: List<Block>){
        registeredBlocks.addAll(blocks)
    }

    fun executeFlow(){

    }

    private fun verify(flows: List<Block>) {
        // unique ids
        val duplicates = flows.map{ it.id }.groupingBy { it }.eachCount().filter { it.value>1 }.map { it.key }
        if (duplicates.size>0)
            throw NonUniqueIdException("All ids must be unique! Duplicate id: ${duplicates.first()}.")

        // Check if the type is already registered
        flows.forEach {
            if (it.type !in  registeredBlocks.map { it.type })
                throw TypeNotRegisteredException("'${it.type}' type is not a registered Block!")
        }

    }

    fun wire(flows: List<Block>) {
        this.flows = flows as MutableList<Block>
        verify(flows)
    }
}


class NonUniqueIdException(msg:String): RuntimeException(msg)
class TypeNotRegisteredException(msg:String): RuntimeException(msg)