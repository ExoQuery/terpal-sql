package io.exoquery.sql.native

import io.exoquery.sql.TerpalDriver
import io.exoquery.sql.sqlite.CallAfterVersion
import io.exoquery.sql.sqlite.TerpalSchema
import kotlinx.coroutines.runBlocking

fun <T> TerpalSchema<T>.toCreateCallbackSync(driver: TerpalDriver): T = runBlocking { create(driver) }
fun <T> TerpalSchema<T>.toMigrateCallbackSync(driver: TerpalDriver, oldVersion: Long, newVersion: Long, vararg callbacks: CallAfterVersion): T =
  runBlocking { migrate(driver, oldVersion, newVersion, *callbacks) }