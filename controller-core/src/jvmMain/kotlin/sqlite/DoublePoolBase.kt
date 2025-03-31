package io.exoquery.controller.sqlite

actual fun getNumProcessorsOnPlatform(): Int = Runtime.getRuntime().availableProcessors()
