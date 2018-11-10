import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.0"
    application
}

application{
    mainClassName = "App"
}

group = "nl.dorost.flow"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {

    compile(kotlin("stdlib-jdk8"))

    compile("com.moandjiezana.toml:toml4j:0.7.2")
    compile("io.github.microutils:kotlin-logging:1.6.20")
    compile(group = "com.walterjwhite.infrastructure.dependencies", name = "slf4j-api", version = "0.0.15")
    compile(group = "com.github.kittinunf.fuel", name = "fuel", version = "1.15.0")

    compile(group = "org.slf4j", name = "slf4j-simple", version = "1.8.0-beta2")

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

