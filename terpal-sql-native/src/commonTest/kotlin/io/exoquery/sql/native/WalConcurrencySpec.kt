package io.exoquery.sql.native

import io.exoquery.sql.WalConcurrencyOps
import io.exoquery.sql.native.WalTestDatabase.ctx
import kotlin.test.BeforeTest
import kotlin.test.Test

class WalConcurrencySpec {
  val ops by lazy { WalConcurrencyOps(ctx) }

  @BeforeTest
  fun clearTables() = ops.clearTables()
  @Test
  fun `Write Should Not Block Read`() =
    ops.`Write_Should_Not_Block_Read`()
}
