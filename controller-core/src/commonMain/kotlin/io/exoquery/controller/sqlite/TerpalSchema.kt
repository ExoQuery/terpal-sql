package io.exoquery.controller.sqlite

import io.exoquery.controller.Controller

interface TerpalSchema<T> {
  val version: Long

  /**
   * Use [driver] to create the schema from scratch. Assumes no existing database state.
   */
  suspend fun create(driver: Controller): T

  /**
   * Use [driver] to migrate from schema [oldVersion] to [newVersion].
   * Each of the [callbacks] are executed during the migration whenever the upgrade to the version specified by
   * [CallAfterVersion.afterVersion] has been completed.
   */
  suspend fun migrate(driver: Controller, oldVersion: Long, newVersion: Long, vararg callbacks: CallAfterVersion): T
}

/**
 * Represents a block of code [block] that should be executed during a migration after the migration
 * has finished migrating to [afterVersion].
 */
class CallAfterVersion(
  val afterVersion: Long,
  val block: suspend (Controller) -> Unit,
)

object EmptyTerpalSchema : TerpalSchema<Unit> {
  override val version: Long = 0
  override suspend fun create(driver: Controller) {
  }
  override suspend fun migrate(driver: Controller, oldVersion: Long, newVersion: Long, vararg callbacks: CallAfterVersion) {
  }
}
