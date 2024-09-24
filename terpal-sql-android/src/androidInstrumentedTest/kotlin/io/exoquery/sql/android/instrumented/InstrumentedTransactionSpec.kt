package io.exoquery.sql.android.instrumented

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.exoquery.sql.PersonSchema
import io.exoquery.sql.TransactionSpecOps
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.BeforeTest

@RunWith(AndroidJUnit4::class)
class InstrumentedTransactionSpec: InstrumentedSpec {
  val ctx get() = createDriver("tran_spec.db", PersonSchema)
  val ops get() = TransactionSpecOps(ctx)

  @BeforeTest
  fun clearTables() = ops.clearTables()
  @Test
  fun success() = ops.success()
  @Test
  fun failure() = ops.failure()
  @Test
  fun nested() = ops.nested()
}
