package io.exoquery.sql.native

import io.exoquery.sql.TransactionSpecOps
import io.exoquery.sql.native.TestDatabase.ctx
import kotlin.test.BeforeTest
import kotlin.test.Test

class TransactionSpec {
  val ops by lazy { TransactionSpecOps(ctx) }

  @BeforeTest
  fun clearTables() = ops.clearTables()
  @Test
  fun success() = ops.success()
  @Test
  fun failure() = ops.failure()
  @Test
  fun nested() = ops.nested()
}
