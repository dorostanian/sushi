import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.0"
    application
    `maven-publish`
    maven
}

application{
    mainClassName = "App"
}

group = "nl.dorost.flow"
version = "1.0-SNAPSHOT"
val ktor_version = "1.0.0-beta-3"
kotlin.experimental.coroutines = Coroutines.ENABLE

repositories {
    mavenCentral()
    jcenter()
    mavenLocal()
    maven { url = uri("https://dl.bintray.com/kotlin/ktor") }
    maven { url = uri("https://dl.bintray.com/kotlin/kotlin-eap") }
}

dependencies {

    compile(kotlin("stdlib-jdk8"))

    compile("com.github.kittinunf.fuel:fuel:1.15.0")
    compile("com.moandjiezana.toml:toml4j:0.7.2")
    compile("io.ktor:ktor-server-netty:$ktor_version")
    compile("org.apache.commons:commons-text:1.6")
    compile("io.github.microutils:kotlin-logging:1.6.20")
    compile("org.slf4j:slf4j-api:1.8.0-beta2")
    compile("org.slf4j:slf4j-simple:1.8.0-beta2")

    compile("com.fasterxml.jackson.core:jackson-databind:2.7.1-1")
    compile("com.fasterxml.jackson.module:jackson-module-kotlin:2.7.1-2")
    compile("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.7.1")

    testCompile("junit", "junit", "4.12")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

