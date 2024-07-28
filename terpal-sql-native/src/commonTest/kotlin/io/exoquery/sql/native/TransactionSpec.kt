package io.exoquery.sql.native

import io.exoquery.sql.TransactionSpecOps
import io.exoquery.sql.native.TestDatabase.ctx
import kotlin.test.BeforeTest
import kotlin.test.Test

class TransactionSpec {
  val ops get() = TransactionSpecOps(ctx, ctx::runRaw)

  @BeforeTest
  fun clearTables() = ops.clearTables()
  @Test
  fun success() = ops.success()
  @Test
  fun failure() = ops.failure()
  @Test
  fun nested() = ops.nested()
}
