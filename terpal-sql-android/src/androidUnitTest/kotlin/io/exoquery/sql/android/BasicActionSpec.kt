package io.exoquery.sql.android

import io.exoquery.sql.BasicActionOps
import org.junit.Ignore
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.BeforeTest
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class BasicActionSpec {
  val ctx get() = TestDatabase.ctx
  val ops = BasicActionOps(ctx, ctx::runRaw)

  @BeforeTest
  fun clearTables() = ops.clearTables()

  @Test
  fun `Basic Insert`() = ops.BasicInsert()

  @Ignore // Not supported with the SQLiter driver being used in Robolectric
  @Test
  fun `Insert Returning Record Id`()  = ops.InsertReturningId()

  @Ignore // Not supported with the SQLiter driver being used in Robolectric
  @Test
  fun `Insert Returning`()  = ops.InsertReturning()

  @Test
  fun `Insert Returning Record`()  = ops.InsertReturningId()
}
