import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
  id("conventions")
  kotlin("jvm")
}

version = extra["controllerVersion"].toString()

repositories {
  mavenCentral()
  mavenLocal()
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  api(project(":controller-core"))

  // Coroutines + reactive bridge
  api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.8.1")

  // R2DBC SPI only (no specific driver)
  api("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")

  testImplementation(kotlin("test"))
}
