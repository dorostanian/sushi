package nl.dorost.flow

import com.moandjiezana.toml.Toml
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors



fun main(args: Array<String>) {

    val flowEngine = FlowEngine()

    val flows = Files.walk(Paths.get("flows/")).filter { Files.isRegularFile(it) }.collect(Collectors.toList()).flatMap {
        val toml = Toml().read(it.toFile())
        val blocks = flowEngine.parseToBlocks(toml)
        blocks
    }

    flowEngine.wire(flows)
    flowEngine.executeFlow()
}

