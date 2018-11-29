package nl.dorost.flow.core

interface BlockListener {
    fun updateReceived(context: MutableMap<String, Any>? = null, message: String? = null)
}