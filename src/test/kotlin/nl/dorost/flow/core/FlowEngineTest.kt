package nl.dorost.flow.core

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import mu.KotlinLogging
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class FlowEngineTest {

    lateinit var flowEngine: FlowEngine

    @BeforeAll
    fun setup() {
        flowEngine = FlowEngine()
        flowEngine.registerListeners(
            listOf(
                object : BlockListener {
                    val LOG = KotlinLogging.logger("TestLogger")
                    override fun updateReceived(context: MutableMap<String, Any>?, message: String?) {
                        LOG.info("Message: $message")
                    }
                }
            )
        )
    }

    @Test
    fun `Test an small flow of blocks`() {
        val flows = mutableListOf(
            Action().apply {
                name = "action 1"
                id = "1"
                type = "constant"
                source = true
                params = mutableMapOf("value" to "2")
                nextBlocks = mutableListOf("2")
            },
            Action().apply {
                name = "action 2"
                id = "2"
                type = "log"
                nextBlocks = mutableListOf("3")
            },
            Action().apply {
                name = "action 3"
                id = "3"
                type = "log"
            }
        )

        flowEngine.wire(flows)
        flowEngine.executeFlow()
        flowEngine.await()

    }


    @Test
    fun `Test an small flow of blocks with Branch`() {
        val flows = mutableListOf(
            Action().apply {
                name = "action 1"
                id = "1"
                type = "constant"
                source = true
                params = mutableMapOf("value" to "2")
                nextBlocks = mutableListOf("2")
            },
            Action().apply {
                name = "action 2"
                id = "2"
                type = "log"
                nextBlocks = mutableListOf("3")
            },
            Action().apply {
                name = "action 3"
                id = "3"
                type = "constant"
                params = mutableMapOf("value" to "5")
                nextBlocks = mutableListOf("b-1")
            },
            Branch().apply {
                name = "Branch 1"
                id = "b-1"
                on = "value"
                mapping = mutableMapOf("4" to "out-1", "5" to "out-2")
            },
            Action().apply {
                name = "action 4"
                id = "out-1"
                type = "log"
            },
            Action().apply {
                name = "action 5"
                id = "out-2"
                type = "log"
            }
        )

        flowEngine.wire(flows)
        flowEngine.executeFlow()
        flowEngine.await()

        Assertions.assertEquals(1, flowEngine.flows.count { it.skipped })
    }


    @Test
    fun `Test an output of the flow`() {
        val flows = mutableListOf(
            Action().apply {
                name = "Json in"
                id = "1"
                type = "json-in"
                source = true
                params = mutableMapOf("value" to "{'name':'amin', 'family':'dorostanian'}")
                nextBlocks = mutableListOf("2")
            },
            Action().apply {
                name = "Log it"
                id = "2"
                type = "log"
                nextBlocks = mutableListOf("3")
            }, Action().apply {
                name = "Send POST request"
                id = "3"
                type = "http-post"
                nextBlocks = mutableListOf("4")
                params = mutableMapOf(
                    "url" to "https://postman-echo.com/post",
                    "ContentType" to "application/json"
                )

            }, Action().apply {
                name = "Log it and return"
                id = "4"
                type = "log"
                returnAfterExec = true
            }
        )

        flowEngine.wire(flows)
        flowEngine.executeFlow()
        flowEngine.await()

        val response = flowEngine.flows.first { it.id == flowEngine.returnedBlockId }.output?.getCompleted()

        val parser = Parser()
        val jsonResponse = parser.parse(StringBuilder(response!!["response"] as String)) as JsonObject
        assertEquals("{'name':'amin', 'family':'dorostanian'}", jsonResponse["form"].toString())

    }


}