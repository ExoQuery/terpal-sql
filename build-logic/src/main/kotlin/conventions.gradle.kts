import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

repositories {
    //mavenLocal() // Don't include this, it causes all sorts of build horror
    mavenCentral()
    mavenLocal()
}

val controllerVersion = "3.2.3"
val pluginVersion = "2.0.0.PL"

extra["controllerVersion"] = controllerVersion
extra["pluginVersion"] = pluginVersion

group = "io.exoquery"
// terpal-* inherits the version from here. The others override it
version = "${pluginVersion}-${controllerVersion}"


check("$version".isNotBlank() && version != "unspecified")
    { "invalid version $version" }

tasks.withType<Test> {
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

        // Get the summary of the tests in a neatly printed format
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
