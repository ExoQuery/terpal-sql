import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22"

    id("conventions")
    id("publish")
    id("io.exoquery.terpal-plugin") version "1.9.22-1.0.0-RC2.8"
    kotlin("plugin.serialization") version "1.9.22"
}

val thisVersion = version

// Enable logging of wrappers
//tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
//    compilerOptions {
//        freeCompilerArgs.addAll(
//            listOf(
//                "-P",
//                "plugin:io.exoquery.terpal-plugin:traceWrappers=true"
//            )
//        )
//    }
//}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
        // Otherwise will have: Could not resolve io.exoquery:pprint-kotlin:2.0.1.
        // Incompatible because this component declares a component, compatible with Java 11 and the consumer needed a component, compatible with Java 8
        java {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        // If I remove this I get:
        //  'compileJava' task (current target is 11) and 'kaptGenerateStubsKotlin' task (current target is 1.8) jvm target compatibility should be set to the same Java version.
        // Not sure why
        //jvmTarget.set(JvmTarget.JVM_11)
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

kotlin {
    jvmToolchain(11)
}

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
    implementation("org.xerial:sqlite-jdbc:3.42.0.1")
    testImplementation("com.oracle.ojdbc:ojdbc8:19.3.0.0")

    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest.extensions:kotest-extensions-testcontainers:2.0.2")

    testApi(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:16.2.0"))
    testImplementation("org.flywaydb:flyway-core:7.15.0") // corresponding to embedded-postgres
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
