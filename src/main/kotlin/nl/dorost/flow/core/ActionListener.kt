package nl.dorost.flow.core

interface ActionListener {
    fun actionExecuted(actionOutput: MutableMap<String, Any>)
}