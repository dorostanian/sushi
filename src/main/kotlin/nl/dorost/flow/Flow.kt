package nl.dorost.flow


abstract class Block(
    open val name: String,
    open val id: String?,
    open val type: String,
    open val params: MutableMap<String, String> = mutableMapOf(),
    open val nextBlocks: MutableList<String> = mutableListOf()
)

data class Container(
    val firstBlock: String,
    val lastBlock: String,
    override  val name: String,
    override val id: String,
    override val type: String,
    override val params: MutableMap<String, String>,
    override val nextBlocks: MutableList<String>
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
    override  val id: String,
    override  val type: String,
    override  val params: MutableMap<String, String>,
    override  val nextBlocks: MutableList<String>
): Block(name, id, type, params, nextBlocks)



class FlowEngine(){

    var flows: MutableList<Block> = mutableListOf()


    fun registerBlocks(blocks: List<Block>){

    }

    fun executeFlow(){

    }

    private fun verify(flows: List<Block>) {
        // unique ids
        if (flows.distinctBy { it.id }.size != flows.size)
            throw NonUniqueIdException("All ids must be unique!")
    }

    fun wire(flows: List<Block>) {
        this.flows = flows as MutableList<Block>
        verify(flows)
    }
}


class NonUniqueIdException(msg:String): RuntimeException(msg)