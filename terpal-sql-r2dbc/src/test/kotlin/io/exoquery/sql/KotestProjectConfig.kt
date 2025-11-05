package io.exoquery.sql

import io.kotest.core.config.AbstractProjectConfig

object KotestProjectConfig : AbstractProjectConfig() {
  override suspend fun beforeProject() {
    // Ensure EmbeddedPostgres is started before any specs run
    TestDatabasesR2dbc.embeddedPostgres
  }

  override suspend fun afterProject() {
    // Ensure EmbeddedPostgres is closed after all specs complete
    try { TestDatabasesR2dbc.embeddedPostgres.close() } catch (_: Throwable) {}
  }
}
