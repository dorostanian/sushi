package nl.dorost.flow.dao

import com.google.auth.Credentials
import com.google.cloud.Timestamp
import com.google.cloud.datastore.*
import nl.dorost.flow.MissingFieldException
import nl.dorost.flow.blockKind
import nl.dorost.flow.dto.ActionDto
import nl.dorost.flow.dto.InnerActionDto
import org.slf4j.LoggerFactory

interface BlocksDao {
    fun registerNewAction(action: ActionDto): ActionDto
    fun getActionByType(type: String): ActionDto?
    fun getAllSecondaryActions(): List<ActionDto>
}


class BlocksDaoImpl(val credentials: Credentials, val projectId: String, val kind: String) : BlocksDao {

    val logger = LoggerFactory.getLogger(this.javaClass.name)


    private val datastore: Datastore
    private val keyFactory: KeyFactory

    init {
        val options = DatastoreOptions.newBuilder().setCredentials(credentials).setProjectId(projectId).build()
        datastore = options.service
        keyFactory = datastore.newKeyFactory().setKind(kind)
    }


    override fun registerNewAction(action: ActionDto): ActionDto {
        val entity = action.toEntity()
        val newId = datastore.add(entity)
        return action.copy(id = newId.key.id)
    }

    override fun getActionByType(type: String): ActionDto? {
        val query = Query.newEntityQueryBuilder()
            .setFilter(StructuredQuery.PropertyFilter.eq("type", type))
            .setKind(kind)
            .setLimit(1)
            .build()
        val resultList = datastore.run(query)
        return if (resultList.hasNext())
            resultList.next().toAction()
        else
            null
    }

    override fun getAllSecondaryActions(): List<ActionDto> {
        val query = Query.newEntityQueryBuilder()
            .setKind(kind)
            .build()
        val resultList = datastore.run(query)
        return resultList.iterator().asSequence().map { it.toAction() }.toList()
    }

    private fun List<InnerActionDto>.toEntity(): List<EntityValue> =
        this.map { EntityValue.newBuilder(it.toEntity()).build() }

    private fun InnerActionDto.toEntity(): FullEntity<*>? =
        Entity.newBuilder(keyFactory.newKey())
            .set("type", this.type)
            .set("id", this.id)
            .set("source", this.source)
            .set("return-after-execution", this.returnAfterExecution)
            .set("next-blocks", this.nextBlocks.map { StringValue.of(it) })
            .build()


    private fun Entity.toAction() = ActionDto(
        id = this.key.id,
        userId = this.getLong("user-id"),
        creationTime = this.getTimestamp("creation-time"),
        type = this.getString("type"),
        name = this.getString("name"),
        description = this.getString("description"),
        innerBlocks = this.getList<EntityValue>("innner-blocks").toInnerBlocks(),
        params = this.getList<StringValue>("params").map { it.get() }
    )

    private fun MutableList<EntityValue>.toInnerBlocks(): List<InnerActionDto> = this.map {
        it.toInnerBlock()
    }

    private fun EntityValue.toInnerBlock(): InnerActionDto {
        val entity = this.get()
        return InnerActionDto(
            id = entity.getString("id"),
            type = entity.getString("type"),
            nextBlocks = entity.getList<StringValue>("next-blocks").map { it.get() }.toMutableList(),
            source = entity.getBoolean("source"),
            returnAfterExecution = entity.getBoolean("return-after-execution")

        )
    }

    private fun ActionDto.toEntity(): FullEntity<*>? = Entity.newBuilder(keyFactory.newKey())
        .set("type", this.type)
        .set("name", this.name ?: "")
        .set("creation-time", Timestamp.now())
        .set("description", this.description ?: "")
        .set("params", this.params.map {StringValue.of(it) })
        .set("user-id", this.userId ?: throw MissingFieldException("User ID is missing!"))
        .set("innner-blocks", this.innerBlocks.toEntity())
        .build()

}








