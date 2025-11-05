package io.exoquery.sql.postgres

import io.exoquery.controller.runActions
import io.exoquery.controller.r2dbc.R2dbcController

object SchemaInitR2dbc {
  @Volatile private var applied: Boolean = false
  @Volatile private var initializing: Boolean = false
  @Volatile private var cachedSql: String? = null

  suspend fun ensureApplied(ctx: R2dbcController) {
    if (applied) return

    var doInit = false
    synchronized(this) {
      if (!applied && !initializing) {
        initializing = true
        if (cachedSql == null) {
          val schemaPath = "/db/postgres-schema.sql"
          val resource = this::class.java.getResource(schemaPath)
            ?: throw NullPointerException("The postgres script path `$schemaPath` was not found")
          cachedSql = resource.readText()
        }
        doInit = true
      }
    }

    if (doInit) {
      ctx.runActions(cachedSql!!)
      synchronized(this) {
        applied = true
        initializing = false
      }
      return
    }

    // Wait for initialization to complete
    while (!applied) {
      kotlinx.coroutines.delay(10)
    }
  }
}
