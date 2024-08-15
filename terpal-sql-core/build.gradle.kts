plugins {
  kotlin("multiplatform") version "1.9.22"

  id("maven-publish")
  id("conventions-multiplatform")
  id("publish")

  id("com.google.devtools.ksp") version "1.9.22-1.0.17"
}

repositories {
  mavenCentral()
}

kotlin {
  jvm {
    jvmToolchain(11)
  }

  linuxX64() {

  }
  //macosX64()
  //mingwX64()

  sourceSets {
    val commonMain by getting {
    }

   val  commonTest by getting {
     dependencies {
       // Used to ad-hoc some examples but not needed.
       //api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
       //implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
       implementation(kotlin("test"))
       implementation(kotlin("test-common"))
       implementation(kotlin("test-annotations-common"))
     }
    }
  }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions{
    freeCompilerArgs = listOf(
      "-Xcontext-receivers",
      "-P",
      "plugin:io.exoquery.terpal-plugin:traceWrappers=true"
    )
    // Otherwise will have: Could not resolve io.exoquery:pprint-kotlin:2.0.1.
    // Incompatible because this component declares a component, compatible with Java 11 and the consumer needed a component, compatible with Java 8
    java {
      sourceCompatibility = JavaVersion.VERSION_11
      targetCompatibility = JavaVersion.VERSION_11
    }
  }
}