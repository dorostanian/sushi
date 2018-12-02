package nl.dorost.flow.dao

import com.google.auth.Credentials
import com.google.cloud.datastore.Datastore
import com.google.cloud.datastore.DatastoreOptions
import com.google.cloud.datastore.KeyFactory
import nl.dorost.flow.dto.UserDto
import org.slf4j.LoggerFactory

interface UsersDao{
    fun createUser(userDto: UserDto)
}


class UserDaoImpl(credentials: Credentials, projectId: String, kind: String): UsersDao{


    val logger = LoggerFactory.getLogger(this.javaClass.name)


    private val datastore: Datastore
    private val keyFactory: KeyFactory

    init {
        val options = DatastoreOptions.newBuilder().setCredentials(credentials).setProjectId(projectId).build()
        datastore = options.service
        keyFactory = datastore.newKeyFactory().setKind(kind)
    }
    override fun createUser(userDto: UserDto) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}