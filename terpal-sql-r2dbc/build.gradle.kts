import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
  id("conventions")
  kotlin("multiplatform")
  id("io.exoquery.terpal-plugin") version "2.2.0-2.0.0.PL"
  kotlin("plugin.serialization") version "2.2.0"
}

val thisVersion = version

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
  compilerOptions {
    freeCompilerArgs.add("-Xcontext-receivers")
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
    exceptionFormat = TestExceptionFormat.FULL
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
    val jvmMain by getting {
      kotlin.srcDir("src/main/kotlin")
      resources.srcDir("src/main/resources")

      dependencies {
        api(project(":terpal-sql-core"))
        api(project(":controller-r2dbc"))

        api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
        api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.8.1")

        // R2DBC Postgres driver
        api("org.postgresql:r2dbc-postgresql:1.0.5.RELEASE")
        // R2DBC SQL Server driver
        api("io.r2dbc:r2dbc-mssql:1.0.2.RELEASE")
        // R2DBC MySQL driver
        api("io.asyncer:r2dbc-mysql:1.1.0")
        // R2DBC H2 driver
        api("io.r2dbc:r2dbc-h2:1.0.0.RELEASE")
        // R2DBC Oracle driver
        api("com.oracle.database.r2dbc:oracle-r2dbc:1.2.0")
      }
    }
    val jvmTest by getting {
      kotlin.srcDir("src/test/kotlin")
      resources.srcDir("src/test/resources")

      dependencies {
        api(project(":controller-r2dbc"))
        api(project(":terpal-sql-core-testing"))

        implementation("io.exoquery:pprint-kotlin:2.0.2")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
        implementation("io.kotest:kotest-runner-junit5:5.9.1")

        // Embedded Postgres for tests (same as JDBC module)
        implementation("io.zonky.test:embedded-postgres:2.0.7")
        implementation("io.zonky.test.postgres:embedded-postgres-binaries-linux-amd64:16.2.0")
        implementation("org.flywaydb:flyway-core:7.15.0")
      }
    }
  }
}
