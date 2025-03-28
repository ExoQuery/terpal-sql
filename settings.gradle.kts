pluginManagement {
  includeBuild("build-logic")

  repositories {
    gradlePluginPortal()
    mavenCentral()
    mavenLocal()
    google()
  }
}

include("terpal-sql-core-context")
include("terpal-sql-jdbc-context")
include("terpal-sql-native-context")
include("terpal-sql-android-context")

include("terpal-sql-core")
include("terpal-sql-core-testing")
include("terpal-sql-jdbc")
include("terpal-sql-native")
include("terpal-sql-android")


rootProject.name = "terpal-sql"
