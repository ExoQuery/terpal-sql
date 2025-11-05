import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
  id("conventions")
  kotlin("multiplatform")
  id("com.android.library")
  kotlin("plugin.serialization") version "2.2.0"
  // Already on the classpath
  //id("org.jetbrains.kotlin.android") version "1.9.23"
}

version = extra["controllerVersion"].toString()

android {
  namespace = "io.exoquery.sql"

  compileSdk = 34
  defaultConfig {
    minSdk = 26
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  testOptions  {
    unitTests.isIncludeAndroidResources = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin {
  if (HostManager.hostIsLinux || !project.hasProperty("isCI")) {

    androidTarget {
      compilations.all {
        compileTaskProvider {
          compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
          }
        }
      }
      publishLibraryVariants("release", "debug")
    }

    sourceSets {

      androidMain.dependencies {
        dependencies {
          api(project(":controller-core"))

          api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
          api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
          api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
          implementation("androidx.sqlite:sqlite-framework:2.4.0")
        }
      }

      val androidInstrumentedTest by getting {
        dependencies {
          implementation(project(":terpal-sql-core-testing"))

          implementation(kotlin("test-junit"))
          implementation("junit:junit:4.13.2")
          implementation("org.robolectric:robolectric:4.13")
          implementation("androidx.test:core:1.6.1")
          implementation("androidx.test:runner:1.6.1")
          implementation("app.cash.sqldelight:android-driver:2.0.2")
          implementation("androidx.test.ext:junit:1.1.3")
          implementation("androidx.test.espresso:espresso-core:3.4.0")
        }
      }

      val androidUnitTest by getting {
        dependencies {
          implementation(project(":terpal-sql-core-testing"))

          implementation(kotlin("test-junit"))
          implementation("junit:junit:4.13.2")
          implementation("org.robolectric:robolectric:4.13")
          implementation("androidx.test:core:1.6.1")
          implementation("androidx.test:runner:1.6.1")
          implementation("app.cash.sqldelight:android-driver:2.0.2")
          implementation("androidx.test.ext:junit:1.1.3")
          implementation("androidx.test.espresso:espresso-core:3.4.0")
        }
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
