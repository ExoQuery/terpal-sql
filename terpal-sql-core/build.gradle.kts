import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("conventions")
    id("publish")
    id("io.exoquery.terpal-plugin") version "2.0.0-0.2.0"
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
}

dependencies {

    api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest.extensions:kotest-extensions-testcontainers:2.0.2")

    testApi(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:16.2.0"))
    testImplementation("org.flywaydb:flyway-core:7.15.0") // corresponding to embedded-postgres
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
