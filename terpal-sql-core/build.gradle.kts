plugins {
  kotlin("multiplatform") version "1.9.22"
}

repositories {
  mavenCentral()
}

kotlin {
  linuxX64()

  sourceSets {
    commonMain {
    }

    commonTest {
    }
  }
}
