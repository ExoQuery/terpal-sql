import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
  id("conventions")
  kotlin("multiplatform")
}

version = extra["controllerVersion"].toString()

repositories {
  mavenCentral()
  mavenLocal()
}

kotlin {
  jvmToolchain(17)
  jvm {
  }
  java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  sourceSets {
    val jvmMain by getting {
      kotlin.srcDir("src/main/kotlin")
      resources.srcDir("src/main/resources")
      dependencies {
        api(project(":controller-core"))
        // Coroutines + reactive bridge
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.8.1")
        // R2DBC SPI only (no specific driver)
        api("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")
        compileOnly("org.postgresql:r2dbc-postgresql:1.0.5.RELEASE")
      }
    }
    val jvmTest by getting {
      kotlin.srcDir("src/test/kotlin")
      resources.srcDir("src/test/resources")
      dependencies {
        implementation(kotlin("test"))
      }
    }
  }
}
