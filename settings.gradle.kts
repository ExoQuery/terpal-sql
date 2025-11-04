pluginManagement {
  includeBuild("build-logic")

  repositories {
    gradlePluginPortal()
    mavenCentral()
    mavenLocal()
    google()
  }
}

include("controller-core")
include("controller-jdbc")
include("controller-native")
include("controller-android")
include("controller-r2dbc")

include("terpal-sql-core")
include("terpal-sql-core-testing")
include("terpal-sql-jdbc")
include("terpal-sql-native")
include("terpal-sql-android")
include("terpal-sql-r2dbc")

rootProject.name = "terpal-sql"
