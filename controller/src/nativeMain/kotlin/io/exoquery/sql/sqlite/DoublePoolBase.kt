package io.exoquery.sql.sqlite

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
actual fun getNumProcessorsOnPlatform(): Int = Platform.getAvailableProcessors()
