package nl.dorost.flow

import nl.dorost.flow.core.FlowEngine
import org.junit.Test

class FlowEngineTest {

    @Test
    fun executeFlow() {

        val flowEngine = FlowEngine()
        val flows = flowEngine.readFlowsFromDir("flows/")

        flowEngine.wire(flows)
        flowEngine.executeFlow()
    }

}