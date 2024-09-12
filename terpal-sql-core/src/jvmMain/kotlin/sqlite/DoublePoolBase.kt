package io.exoquery.sql.sqlite

actual fun getNumProcessorsOnPlatform(): Int = Runtime.getRuntime().availableProcessors()
