import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("conventions")
  kotlin("multiplatform")
  id("io.exoquery.terpal-plugin") version "2.1.20-2.0.0.PL"
  kotlin("plugin.serialization") version "2.1.20"
  id("nativebuild")
}

kotlin {
  jvmToolchain(17)
  jvm {
  }

  java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  // Enabling this causes: > Querying the mapped value of task ':commonizeNativeDistribution' property 'rootOutputDirectoryProperty$kotlin_gradle_plugin_common' before task ':commonizeNativeDistribution' has completed is not supported
  // androidNativeX64()

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(project(":controller-core"))

        api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
        api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        //api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
        api("io.exoquery:terpal-runtime:2.1.20-2.0.0.PL")
        implementation("org.jetbrains.kotlinx:atomicfu:0.23.1")
        implementation("com.sschr15.annotations:jb-annotations-kmp:24.1.0+apple")
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
  google()
}
