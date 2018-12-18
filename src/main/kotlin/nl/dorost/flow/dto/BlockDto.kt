package nl.dorost.flow.dto

import com.google.cloud.Timestamp

data class ContainerDto(
    val id: Long? = null,
    val name: String?,
    val description: String?,
    val type: String,
    val innerBlocks: List<InnerActionDto>,
    val params: List<String>,
    val userId: Long? = null,
    val creationTime: Timestamp? = null,
    val outputKeys: List<String> = listOf(),
    val public: Boolean
)

data class InnerActionDto(
    val id: String,
    val type: String,
    val source: Boolean = false,
    val returnAfterExecution: Boolean= false,
    val params: Map<String, String> = mapOf(),
    val nextBlocks: MutableList<String>

)



data class UserDto(
    val id: Long?,
    val name: String? = null,
    val sessionId: String,
    val email: String
)