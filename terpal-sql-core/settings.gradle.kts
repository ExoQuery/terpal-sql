pluginManagement {
  includeBuild("../build-logic")

  //resolutionStrategy {
  //  eachPlugin {
  //    if (requested.id.namespace == "io.exoquery" && requested.id.name == "terpal-runtime") {
  //      println("---------------------- HERE ----------------------")
  //      useModule("io.exoquery:terpal-runtime:1.9.22-0.3.2")
  //    }
  //  }
  //}

  repositories {
    gradlePluginPortal()
    mavenCentral()
    mavenLocal()
  }

}
