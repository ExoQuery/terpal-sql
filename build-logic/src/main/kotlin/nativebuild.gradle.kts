import org.jetbrains.kotlin.konan.target.HostManager

plugins {
  id("conventions")
  kotlin("multiplatform")
}

repositories {
    //mavenLocal() // Don't include this, it causes all sorts of build horror
    mavenCentral()
    mavenLocal()
}


// When inhereting `nativebuild` put these entries into your local build.gradle.kts.
// ...can they be enabled only here if you don't use kotlin("multiplatform") ???
//tasks.named { it == "linuxX64Test" }.configureEach { enabled = HostManager.hostIsLinux }
//tasks.named { it == "linkDebugTestLinuxX64" }.configureEach { enabled = HostManager.hostIsLinux }
//tasks.named { it == "mingwX64Test" }.configureEach { enabled = HostManager.hostIsMingw }
//tasks.named { it == "linkDebugTestMingwX64" }.configureEach { enabled = HostManager.hostIsMingw }


kotlin {

  val isCI = project.hasProperty("isCI")
  val fullLocal = !isCI && System.getenv("TERPAL_FULL_LOCAL")?.toBoolean() ?: false

  if (HostManager.hostIsLinux || fullLocal) {
    linuxX64()
    if (isCI) {
      linuxArm64()

      // Need to know about this since we publish the -tooling metadata from
      // the linux containers. Although it doesn't build these it needs to know about them.
      macosX64()
      iosX64()
      iosArm64()
      watchosArm32()
      watchosArm64()
      watchosX64()
      tvosArm64()
      tvosX64()
      macosArm64()
      iosSimulatorArm64()
      mingwX64()
      // Terpal-Runtime not published for these yet
      //watchosDeviceArm64()
      //tvosSimulatorArm64()
      //watchosSimulatorArm64()
    }
  }

  if (HostManager.hostIsMingw || fullLocal) {
    mingwX64()
  }

  if (HostManager.hostIsMac || fullLocal) {
    macosX64()
    // Build the other targets only if we are on the CI
    if (isCI) {
      iosX64()
      iosArm64()
      watchosArm32()
      watchosArm64()
      watchosX64()
      tvosArm64()
      tvosX64()
      macosArm64()
      iosSimulatorArm64()
      // Terpal-Runtime not published for these yet
      //watchosSimulatorArm64()
      //tvosSimulatorArm64()
      //watchosDeviceArm64()
    }
  }
}