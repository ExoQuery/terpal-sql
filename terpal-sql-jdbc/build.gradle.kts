import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("conventions")
    id("publish")
    id("io.exoquery.terpal-plugin") version "1.9.0-0.2.0"
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
}

val thisVersion = version

dependencies {
    // Looks like it knows to do a project-dependency even if there is a version attached (i.e. I guess it ignores the version?)
    api("io.exoquery:terpal-sql-core:${thisVersion}")

    api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    // Optional by the user. This library provides certain utilities that enhance Hikari.
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("com.typesafe:config:1.4.1")

    testImplementation("io.exoquery:pprint-kotlin:2.0.2")
    testImplementation("io.zonky.test:embedded-postgres:2.0.7")
    testImplementation("mysql:mysql-connector-java:8.0.29")
    testImplementation("com.microsoft.sqlserver:mssql-jdbc:7.4.1.jre11")
    testImplementation("com.h2database:h2:2.2.224")
    testImplementation("org.xerial:sqlite-jdbc:3.42.0.1")
    testImplementation("com.oracle.ojdbc:ojdbc8:19.3.0.0")

    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest.extensions:kotest-extensions-testcontainers:2.0.2")

    testApi(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:16.2.0"))
    testImplementation("org.flywaydb:flyway-core:7.15.0") // corresponding to embedded-postgres
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
