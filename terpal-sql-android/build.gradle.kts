import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
  id("conventions")
  kotlin("multiplatform")
  id("com.android.library")
  id("io.exoquery.terpal-plugin") version "1.9.22-1.0.0-RC3"
  kotlin("plugin.serialization") version "1.9.22"
  // Already on the classpath
  //id("org.jetbrains.kotlin.android") version "1.9.23"
}

android {
  namespace = "io.exoquery.sql"

  compileSdk = 34
  defaultConfig {
    minSdk = 23
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
//  sourceSets {
//    getByName("main") {
//      manifest.srcFile("src/androidMain/AndroidManifest.xml")
//      java.srcDirs("src/androidMain/kotlin")
//      res.srcDirs("src/androidMain/resources")
//    }
//    getByName("test") {
//      java.srcDirs("src/androidTest/kotlin")
//      res.srcDirs("src/androidTest/resources")
//    }
//  }
//  testOptions {
//    unitTests.isIncludeAndroidResources = true
//  }

  testOptions  {
    unitTests.isIncludeAndroidResources = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin {
  if (HostManager.hostIsLinux) {

    androidTarget {
      compilations.all {
        kotlinOptions {
          jvmTarget = "17"
        }
      }
    }

    sourceSets {

      androidMain.dependencies {
        dependencies {
          api(project(":terpal-sql-core"))

          api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
          api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
          api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
          //api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
          api("io.exoquery:terpal-runtime:1.9.22-1.0.0-RC3")

          //implementation("androidx.sqlite:sqlite:2.4.0")
          //implementation("androidx.sqlite:sqlite-ktx:2.4.0")
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


      //androidNativeTest.dependencies {
      //  dependencies {
      //    implementation(kotlin("test"))
      //    implementation(kotlin("test-common"))
      //    implementation(kotlin("test-annotations-common"))
      //    //implementation("io.kotest:kotest-framework-engine:5.9.1")
      //  }
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
