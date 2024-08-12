includeBuild("terpal-sql-core")
includeBuild("terpal-sql-jdbc")
includeBuild("terpal-sql-native")

rootProject.name = "terpal-sql"

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    mavenLocal()
  }
}

