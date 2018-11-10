package nl.dorost.flow

import com.moandjiezana.toml.Toml
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors



fun main(args: Array<String>) {


    val flows = Files.walk(Paths.get("flows/")).filter { Files.isRegularFile(it) }.collect(Collectors.toList()).flatMap {
        val toml = Toml().read(it.toFile())
        val blocks = parseToBlocks(toml)
        blocks
    }

    val flowEngine = FlowEngine()
    flowEngine.wire(flows)
    flowEngine.executeFlow()
}

fun parseToBlocks(toml: Toml): MutableList<Block> {
    val containers = (toml.toMap()["container"] as List<HashMap<String, Any>>).map {
        Container(
            name = it["name"]!! as String,
            type = it["type"]!! as String,
            id = it["id"]!! as String,
            params = it["params"] as HashMap<String, String>,
            firstBlock = it["first"] as String,
            lastBlock = it["last"] as String
        ) as Block
    }

    val actions = (toml.toMap()["action"] as List<HashMap<String, Any>>).map{
        Action(
            name = it["name"]!! as String,
            type = it["type"]!! as String,
            id = it["id"]!! as String,
            params = it.getOrDefault("params", mapOf<String, String>())  as MutableMap<String, String>,
            nextBlocks = it.getOrDefault("next", mutableListOf<String>()) as MutableList<String>
        ) as Block
    }

    val branches = (toml.toMap()["branch"] as List<HashMap<String, Any>>).map {
        Branch(
            name = it["name"]!! as String,
            type = it["type"]!! as String,
            id = it["id"]!! as String,
            params = it["params"] as HashMap<String, String>,
            mapping = it["mapping"] as HashMap<String, String>
        )
    }
    return containers.plus(actions).plus(branches).toMutableList()
}
