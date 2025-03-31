import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeOutputKind
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
  id("conventions")
  kotlin("multiplatform")
  kotlin("plugin.serialization") version "2.1.0"
  id("nativebuild")
}


// Need to disable native targets here as opposed to in `nativebuild` because gradle seems to override
// what is there with defaults coming from `kotlin("multiplatform")` i.e. re-enabling all of the linking phases.
// This happens despite the fact that linking shouldn't even happen if you're not on the right host type.
tasks.named { it == "linuxX64Test" }.configureEach { enabled = HostManager.hostIsLinux }
tasks.named { it == "linkDebugTestLinuxX64" }.configureEach { enabled = HostManager.hostIsLinux }
tasks.named { it == "mingwX64Test" }.configureEach { enabled = HostManager.hostIsMingw }
tasks.named { it == "linkDebugTestMingwX64" }.configureEach { enabled = HostManager.hostIsMingw }

kotlin {

  val thisVersion = version

  if (HostManager.hostIsLinux) {
    linuxX64 {
      compilations.configureEach {
        if (name == "test") {
          cinterops {
            val sqlite by creating {
              // use sqlite3 amalgamation on linux tests to prevent linking issues on new linux distros with dependency libraries which are too recent (for example glibc)
              // see: https://github.com/touchlab/SQLiter/pull/38#issuecomment-867171789
              println("------ Using sqlite3 amalgamation for linux tests: $rootDir/libs/linux/cinterop/sqlite3.def (exists: ${file("$rootDir/libs/linux/cinterop/sqlite3.def").exists()}) ------")
              defFile = file("$rootDir/libs/linux/cinterop/sqlite3.def")
            }
          }
        }
      }
    }
  }

  if (HostManager.hostIsMingw) {
    mingwX64 {
      binaries.configureEach {
        // we only need to link sqlite for the test binaries
        if (outputKind == NativeOutputKind.TEST) {
          linkerOpts += listOf("-Lc:\\msys64\\mingw64\\lib", "-L$rootDir\\libs\\windows".toString(), "-lsqlite3")
        }
      }
    }
  }

  configure(targets.withType<KotlinNativeTarget>().filter {
    listOf(
      "iosX64",
      "iosArm64",
      "tvosX64",
      "tvosArm64",
      "watchosX64",
      "watchosArm32",
      "watchosArm64",
      "macosX64",
      "macosArm64",
      "iosSimulatorArm64",
      "watchosSimulatorArm64",
      "tvosSimulatorArm64"
    ).contains(it.name)
  }) {
    binaries.configureEach {
      // we only need to link sqlite for the test binaries
      if (outputKind == NativeOutputKind.TEST) {
        linkerOpts.add("-lsqlite3")
      }
    }
  }

  //configure(listOf(targets["linuxX64"])) {
  //  compilations.getByName("test") {
  //    //println("---------------- Configuraing test compile: ${this} ----------------")
  //    //kotlinOptions {
  //    //  freeCompilerArgs = listOf("-linker-options", "--allow-shlib-undefined -lsqlite3 -L/usr/lib/x86_64-linux-gnu -L/usr/lib")
  //    //}
  //  }
  //}

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(project(":terpal-sql-core-context"))

        api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
        api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        //api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
        implementation("app.cash.sqldelight:native-driver:2.0.2")
      }
    }

    val commonTest by getting {
      dependencies {
        api(project(":terpal-sql-core-testing"))

        implementation(kotlin("test"))
        implementation(kotlin("test-common"))
        implementation(kotlin("test-annotations-common"))

        //implementation("io.kotest:kotest-framework-engine:5.9.1")
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
