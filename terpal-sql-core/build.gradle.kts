plugins {
  kotlin("multiplatform") version "1.9.22"
}

repositories {
  mavenCentral()
}

kotlin {
  jvm {
    jvmToolchain(11)
  }

  js {
    browser()
    nodejs()
  }
  linuxX64()
  //macosX64()
  mingwX64()

  sourceSets {
    commonMain {
    }

    commonTest {
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