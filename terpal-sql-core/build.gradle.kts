import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  kotlin("multiplatform") version "1.9.22"

  id("conventions")
  id("publish")
  id("io.exoquery.terpal-plugin") version "1.9.22-1.0.0-RC1"
  kotlin("plugin.serialization") version "1.9.22"
}

kotlin {
  jvm {
    jvmToolchain(11)
  }

  java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  // TODO add back the other platforms
  //linuxX64()

  sourceSets {
    val commonMain by getting {
      dependencies {
        api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
        api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        //api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
        api("io.exoquery:terpal-runtime:1.9.22-1.0.0-RC1")
      }
    }

    val commonTest by getting {
      //dependencies {
      //  implementation(kotlin("test"))
      //  implementation(kotlin("test-common"))
      //  implementation(kotlin("test-annotations-common"))
      //}
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
