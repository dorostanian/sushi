package nl.dorost.flow.core

interface BlockListener {
    fun updateReceived(context: MutableMap<String, Any>? = null, message: String? = null, type: MessageType = MessageType.INFO )
}


enum class MessageType {
    INFO, ERROR
}