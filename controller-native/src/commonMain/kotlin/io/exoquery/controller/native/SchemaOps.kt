package io.exoquery.controller.native

import io.exoquery.controller.TerpalDriver
import io.exoquery.controller.sqlite.CallAfterVersion
import io.exoquery.controller.sqlite.TerpalSchema
import kotlinx.coroutines.runBlocking

fun <T> TerpalSchema<T>.toCreateCallbackSync(driver: TerpalDriver): T = runBlocking { create(driver) }
fun <T> TerpalSchema<T>.toMigrateCallbackSync(driver: TerpalDriver, oldVersion: Long, newVersion: Long, vararg callbacks: CallAfterVersion): T =
  runBlocking { migrate(driver, oldVersion, newVersion, *callbacks) }