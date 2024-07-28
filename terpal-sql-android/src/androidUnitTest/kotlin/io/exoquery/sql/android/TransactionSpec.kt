package io.exoquery.sql.android

import io.exoquery.sql.TransactionSpecOps
import io.exoquery.sql.android.TestDatabase.ctx
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.BeforeTest
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
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
