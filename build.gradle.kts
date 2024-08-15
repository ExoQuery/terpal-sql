plugins {
  kotlin("multiplatform") version "1.9.22" apply false
}

allprojects {
    repositories {
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/releases")
        mavenLocal()
    }

    tasks.withType<JavaCompile>() {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }
}
