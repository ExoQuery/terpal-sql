package io.exoquery.sql.native

import io.exoquery.sql.Context
import io.exoquery.sql.sqlite.CallAfterVersion
import io.exoquery.sql.sqlite.TerpalSchema
import kotlinx.coroutines.runBlocking

fun <T> TerpalSchema<T>.toCreateCallbackSync(driver: Context): T = runBlocking { create(driver) }
fun <T> TerpalSchema<T>.toMigrateCallbackSync(driver: Context, oldVersion: Long, newVersion: Long, vararg callbacks: CallAfterVersion): T =
  runBlocking { migrate(driver, oldVersion, newVersion, *callbacks) }