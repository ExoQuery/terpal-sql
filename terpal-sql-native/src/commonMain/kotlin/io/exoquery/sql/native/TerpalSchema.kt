package io.exoquery.sql.native

import io.exoquery.sql.Context
import kotlinx.coroutines.runBlocking

interface TerpalSchema<T> {
  fun toCreateCallbackSync(driver: Context): T = runBlocking { create(driver) }
  fun toMigrateCallbackSync(driver: Context, oldVersion: Long, newVersion: Long, vararg callbacks: AfterVersion): T =
    runBlocking { migrate(driver, oldVersion, newVersion, *callbacks) }


  val version: Long

  /**
   * Use [driver] to create the schema from scratch. Assumes no existing database state.
   */
  suspend fun create(driver: Context): T

  /**
   * Use [driver] to migrate from schema [oldVersion] to [newVersion].
   * Each of the [callbacks] are executed during the migration whenever the upgrade to the version specified by
   * [AfterVersion.afterVersion] has been completed.
   */
  suspend fun migrate(driver: Context, oldVersion: Long, newVersion: Long, vararg callbacks: AfterVersion): T
}

/**
 * Represents a block of code [block] that should be executed during a migration after the migration
 * has finished migrating to [afterVersion].
 */
class AfterVersion(
  val afterVersion: Long,
  val block: suspend (Context) -> Unit,
)
