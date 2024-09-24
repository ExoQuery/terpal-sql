package io.exoquery.sql.native

import io.exoquery.sql.BasicActionOps
import io.exoquery.sql.native.TestDatabase.ctx
import kotlin.test.BeforeTest
import kotlin.test.Test

class BasicActionSpec {
  val ops = BasicActionOps(ctx)

  @BeforeTest
  fun clearTables() = ops.clearTables()

  @Test
  fun `Basic Insert`() = ops.BasicInsert()

  @Test
  fun `Insert Returning Record Id`()  = ops.InsertReturningId()

  @Test
  fun `Insert Returning`()  = ops.InsertReturning()

  @Test
  fun `Insert Returning Record`()  = ops.InsertReturningRecord()
}
