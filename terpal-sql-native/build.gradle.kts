import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  kotlin("multiplatform") version "1.9.22"
  id("conventions")
  id("publish")
  id("io.exoquery.terpal-plugin") version "1.9.22-1.0.0-RC2.8"
  kotlin("plugin.serialization") version "1.9.22"
  //id("io.kotest.multiplatform") version "5.9.1"
}

kotlin {

  val thisVersion = version

  linuxX64()

  sourceSets {
    val commonMain by getting {
      dependencies {
        api("io.exoquery:terpal-sql-core:${thisVersion}")

        api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
        api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        //api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
        api("io.exoquery:terpal-runtime:1.9.22-1.0.0-RC2.8")
        implementation("app.cash.sqldelight:native-driver:2.0.2")

        //implementation("io.kotest:kotest-framework-engine:5.9.1")
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(kotlin("test-common"))
        implementation(kotlin("test-annotations-common"))
      }
    }
  }
}

dependencies {
  commonMainApi("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
}

repositories {
  mavenCentral()
  mavenLocal()
}
