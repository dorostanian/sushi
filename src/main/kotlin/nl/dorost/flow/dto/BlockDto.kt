package nl.dorost.flow.dto

import com.google.cloud.Timestamp

data class ActionDto(
    val id: Long? = null,
    val name: String?,
    val description: String?,
    val type: String,
    val innerBlocks: List<InnerActionDto>,
    val params: List<String>,
    val userId: Long? = null,
    val creationTime: Timestamp? = null
)

data class InnerActionDto(
    val id: String,
    val type: String,
    val source: Boolean = false,
    val returnAfterExecution: Boolean= false,
    val nextBlocks: MutableList<String>

)



data class UserDto(
    val name: String,
    val id: Long
)