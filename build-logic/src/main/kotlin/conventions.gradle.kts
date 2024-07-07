import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

repositories {
    mavenLocal()
    mavenCentral()
}

plugins {
  kotlin("jvm")
}

group = "io.exoquery"
// Everything inherits the version from here
version = "2.0.0-0.2.0"

check("$version".isNotBlank() && version != "unspecified")
    { "invalid version $version" }

tasks.withType<KotlinCompile> {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
}
