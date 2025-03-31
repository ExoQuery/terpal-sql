import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile

plugins {
  id("conventions")
  kotlin("multiplatform")
  kotlin("plugin.serialization") version "2.1.0"
}

val thisVersion = version

// Exclude the jb-annotations-kmp in favor of the official jebrains one (in dependencies below)
configurations.forEach {
  //create("cleanedAnnotations")
  it.exclude(group = "com.sschr15.annotations", module = "jb-annotations-kmp")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
  compilerOptions {
    freeCompilerArgs.add("-Xcontext-receivers")
    // Otherwise will have: Could not resolve io.exoquery:pprint-kotlin:2.0.1.
    // Incompatible because this component declares a component, compatible with Java 11 and the consumer needed a component, compatible with Java 8
    java {
      sourceCompatibility = JavaVersion.VERSION_11
      targetCompatibility = JavaVersion.VERSION_11
    }
  }
}

repositories {
  mavenCentral()
  mavenLocal()
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
  testLogging {
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
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
    jvmMain {
      kotlin.srcDir("src/main/kotlin")
      resources.srcDir("src/main/resources")

      dependencies {
        api(project(":controller"))

        api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
        api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        // Optional by the user. This library provides certain utilities that enhance Hikari.
        implementation("com.zaxxer:HikariCP:5.0.1")
        implementation("com.typesafe:config:1.4.1")
        implementation("org.xerial:sqlite-jdbc:3.42.0.1")
        implementation("org.jetbrains:annotations:24.1.0")
      }
    }
    jvmTest {
      kotlin.srcDir("src/test/kotlin")
      resources.srcDir("src/test/resources")

      dependencies {
        api(project(":terpal-sql-core-testing"))

        implementation("io.exoquery:pprint-kotlin:2.0.2")
        implementation("io.zonky.test:embedded-postgres:2.0.7")
        implementation("mysql:mysql-connector-java:8.0.29")
        implementation("com.microsoft.sqlserver:mssql-jdbc:7.4.1.jre11")
        implementation("com.h2database:h2:2.2.224")
        implementation("com.oracle.ojdbc:ojdbc8:19.3.0.0")
      }
    }
  }
} 