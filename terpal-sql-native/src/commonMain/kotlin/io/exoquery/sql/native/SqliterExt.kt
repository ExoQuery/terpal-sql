package io.exoquery.sql.native

import co.touchlab.sqliter.DatabaseManager
import co.touchlab.sqliter.Statement

fun Statement.resetAndClear() {
  resetStatement()
  clearBindings()
}
