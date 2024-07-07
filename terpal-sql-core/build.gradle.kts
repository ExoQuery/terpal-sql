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

tasks.test {
    useJUnitPlatform()

    testLogging {
        lifecycle {
            events = mutableSetOf(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED)
            exceptionFormat = TestExceptionFormat.FULL

            showExceptions = true
            showCauses = true
            showStackTraces = false
            showStandardStreams = false
        }
        info.events = lifecycle.events
        info.exceptionFormat = lifecycle.exceptionFormat
    }

    val failedTests = mutableListOf<TestDescriptor>()
    val skippedTests = mutableListOf<TestDescriptor>()

    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}

        override fun beforeTest(testDescriptor: TestDescriptor) {}

        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
            when (result.resultType) {
                TestResult.ResultType.FAILURE -> failedTests.add(testDescriptor)
                TestResult.ResultType.SKIPPED -> skippedTests.add(testDescriptor)
                else -> Unit
            }
        }

        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent == null) {
                logger.lifecycle("################ Summary::Start ################")
                logger.lifecycle("Test result: ${result.resultType}")
                logger.lifecycle(
                    "Test summary: ${result.testCount} tests, " +
                      "${result.successfulTestCount} succeeded, " +
                      "${result.failedTestCount} failed, " +
                      "${result.skippedTestCount} skipped")
                failedTests.takeIf { it.isNotEmpty() }?.prefixedSummary("\tFailed Tests")
                skippedTests.takeIf { it.isNotEmpty() }?.prefixedSummary("\tSkipped Tests:")
                logger.lifecycle("################ Summary::End ##################")
            }
        }

        private infix fun List<TestDescriptor>.prefixedSummary(subject: String) {
            logger.lifecycle(subject)
            forEach { test -> logger.lifecycle("\t\t${test.displayName()}") }
        }

        private fun TestDescriptor.displayName() = parent?.let { "${it.name} - $name" } ?: "$name"

    })
}

kotlin {
    jvmToolchain(11)
}

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
        jvmTarget.set(JvmTarget.JVM_11)
    }
}


// Old way of doing it:
//tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
//    kotlinOptions{
//        freeCompilerArgs = listOf("-Xcontext-receivers")
//        // Otherwise will have: Could not resolve io.exoquery:pprint-kotlin:2.0.1.
//        // Incompatible because this component declares a component, compatible with Java 11 and the consumer needed a component, compatible with Java 8
//        java {
//            sourceCompatibility = JavaVersion.VERSION_11
//            targetCompatibility = JavaVersion.VERSION_11
//        }
//    }
//}

repositories {
    mavenCentral()
    mavenLocal()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {

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

    testImplementation("org.testcontainers:mysql:1.19.8")
    testImplementation("org.testcontainers:postgresql:1.19.8")
    testImplementation("org.testcontainers:mssqlserver:1.19.8")
    testImplementation("org.testcontainers:oracle-xe:1.19.8")

    testApi(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:16.2.0"))
    testImplementation("org.flywaydb:flyway-core:7.15.0") // corresponding to embedded-postgres
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
