package nl.dorost.flow.utils

import io.ktor.http.HttpStatusCode

data class ResponseMessage(
    val responseLog: String,
    val data: String? = null,
    val httpCode: HttpStatusCode
)