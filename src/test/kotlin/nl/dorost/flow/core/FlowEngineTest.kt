package nl.dorost.flow.core

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import jdk.nashorn.internal.objects.Global
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import org.awaitility.Awaitility
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.TimeUnit

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


    @Test
    fun `Test asynchronus calls`() {
        val flows = mutableListOf(
            Action().apply {
                name = "input 1"
                id = "1"
                type = "constant"
                source = true
                params = mutableMapOf("value" to "5")
                nextBlocks = mutableListOf("delay-1")
            }, Action().apply {
                name = "input 2"
                id = "2"
                source = true
                type = "constant"
                params = mutableMapOf("value" to "3")
                nextBlocks = mutableListOf("delay-2")
            }, Action().apply {
                name = "Delay 1"
                id = "delay-1"
                params = mutableMapOf("seconds" to "2")
                type = "delay"
                nextBlocks = mutableListOf("3")
            }, Action().apply {
                name = "Delay 2"
                params = mutableMapOf("seconds" to "2")
                id = "delay-2"
                type = "delay"
                nextBlocks = mutableListOf("3")
            }, Action().apply {
                name = "Log the result"
                id = "3"
                type = "log"
            }
        )

        flowEngine.wire(flows)
        flowEngine.executeFlow()

        Awaitility.await().atMost(2200, TimeUnit.MILLISECONDS).until {
            flowEngine.await()
        }
    }

    @Test
    fun `Test container registration`(){
        val flows = mutableListOf(
            Action().apply {
                name = "intial"
                id = "1"
                type = "log"
                source = true
                nextBlocks = mutableListOf("new-type")
            }, Action().apply {
                name = "New registered type"
                id = "new-type"
                type = "container-as-action"
                nextBlocks = mutableListOf("last-block")
            }, Action().apply {
                name = "Last block"
                id = "last-block"
                type = "log"
            }, Container().apply {
                id = "my-container"
                type = "container-as-action"
                firstBlock = "container-1"
                lastBlock = "container-3"
                params = mutableMapOf("something" to "some value")
            },Action().apply {
                name = "cont-1"
                id = "container-1"
                type = "log"
                nextBlocks = mutableListOf("container-2")
            },
            Action().apply {
                name = "cont-2"
                id = "container-2"
                type = "delay"
                params = mutableMapOf("seconds" to "3")
                nextBlocks = mutableListOf("container-3")
            },
            Action().apply {
                name = "cont-3"
                id = "container-3"
                type = "log"
            }
        )
        flowEngine.wire(flows)
        flowEngine.executeFlow()
        flowEngine.await()
    }


}