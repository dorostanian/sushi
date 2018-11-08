package nl.dorost.flow

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class UniverseSizeDto(val maxGalaxies: Int, val maxSystems: Int, val maxPlanets: Int)

data class ResourcesDto(val crystal: Int, val gas: Int, val energy: Int)

data class StarterPlanetDto(val resources: ResourcesDto)

data class ConfigDto(val universeSize: UniverseSizeDto, val starterPlanet: StarterPlanetDto, val roundTime: Int)


fun loadFromFile(path: Path): ConfigDto {
    val mapper = ObjectMapper(YAMLFactory()) // Enable YAML parsing
    mapper.registerModule(KotlinModule()) // Enable Kotlin support

    return Files.newBufferedReader(path).use {
        mapper.readValue(it, ConfigDto::class.java)
    }
}

fun main(args: Array<String>) {
    val configDto = loadFromFile(Paths.get("ex.yml"))
    println(configDto)
}