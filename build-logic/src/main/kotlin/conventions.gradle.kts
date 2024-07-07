import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

repositories {
    mavenLocal()
    mavenCentral()
}

plugins {
  kotlin("jvm")
}

group = "io.exoquery"
// Everything inherits the version from here
version = "2.0.0-0.2.0"

check("$version".isNotBlank() && version != "unspecified")
    { "invalid version $version" }

tasks.withType<KotlinCompile> {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
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
