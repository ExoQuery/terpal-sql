plugins {
  kotlin("jvm") version "1.9.22" apply false
  id("io.github.gradle-nexus.publish-plugin") version "1.1.0" apply false
  kotlin("multiplatform") version "1.9.22" apply false
  id("com.android.library") version "8.2.0" apply false
}

subprojects {
    repositories {
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/releases")
        mavenLocal()
        google()
    }
}

repositories {
  mavenCentral()
  mavenLocal()
  google()
}