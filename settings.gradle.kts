pluginManagement {
  includeBuild("build-logic")

  repositories {
    gradlePluginPortal()
    mavenCentral()
    mavenLocal()
    google()
  }
}

include("terpal-sql-core")
include("terpal-sql-core-testing")
include("terpal-sql-jdbc")
include("terpal-sql-native")
include("terpal-sql-android")

rootProject.name = "terpal-sql"
