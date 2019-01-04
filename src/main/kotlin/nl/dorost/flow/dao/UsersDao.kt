package nl.dorost.flow.dao

import com.google.auth.Credentials
import com.google.cloud.datastore.*
import nl.dorost.flow.dto.UserDto
import org.slf4j.LoggerFactory
import java.util.*

interface UsersDao {
    fun createUser(userDto: UserDto): UserDto
    fun getUserBySessionId(id: String): UserDto?
}

class UserMemoryDaoImpl: UsersDao{

    val users: MutableList<UserDto> = mutableListOf()
    override fun createUser(userDto: UserDto): UserDto {
        users.add(userDto.copy(id = Random().nextLong()))
        return userDto.copy(id = Random().nextLong())
    }

    override fun getUserBySessionId(id: String): UserDto? {
        return users.firstOrNull { it.sessionId==id }
    }

}

class UserDaoImpl(credentials: Credentials, val projectId: String, val kind: String) : UsersDao {


    val logger = LoggerFactory.getLogger(this.javaClass.name)


    private val datastore: Datastore
    private val keyFactory: KeyFactory

    init {
        val options = DatastoreOptions.newBuilder().setCredentials(credentials).setProjectId(projectId).build()
        datastore = options.service
        keyFactory = datastore.newKeyFactory().setKind(kind)
    }

    override fun createUser(userDto: UserDto): UserDto {
        val entity = userDto.toEntity()
        val newId = datastore.add(entity)
        return userDto.copy(id = newId.key.id)
    }

    override fun getUserBySessionId(sessionId: String): UserDto? {
        val query = Query.newEntityQueryBuilder()
            .setFilter(StructuredQuery.PropertyFilter.eq("session-id", sessionId))
            .setKind(kind)
            .setLimit(1)
            .build()
        val resultList = datastore.run(query)
        return if (resultList.hasNext())
            resultList.next().toUserDto()
        else
            null
    }


    private fun UserDto.toEntity(): FullEntity<*>? = Entity.newBuilder(keyFactory.newKey())
        .set("session-id", this.sessionId)
        .set("email", this.email)
        .set("name", this.name)
        .build()

    private fun Entity.toUserDto() = UserDto(
        id = this.key.id,
        name = this.getString("name"),
        email = this.getString("email"),
        sessionId = this.getString("session-id")
    )


}


