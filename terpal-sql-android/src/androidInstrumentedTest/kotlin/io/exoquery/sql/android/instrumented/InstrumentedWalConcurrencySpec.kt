package io.exoquery.sql.android.instrumented

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.exoquery.sql.WalConcurrencyOps
import io.exoquery.sql.WalTestSchema
import io.exoquery.controller.android.AndroidDatabaseController
import org.junit.runner.RunWith
import org.junit.Test
import kotlin.test.BeforeTest

@RunWith(AndroidJUnit4::class)
class InstrumentedWalConcurrencySpec: InstrumentedSpec {
  val ctx by lazy {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    val callback = AndroidSqliteDriver.Callback(WalTestSchema)
    AndroidDatabaseController.fromApplicationContext("wal_test.db", appContext, callback, poolingMode = AndroidDatabaseController.PoolingMode.MultipleReaderWal(3))
  }

  val ops by lazy { WalConcurrencyOps(ctx) }

  @BeforeTest
  fun clearTables() = ops.clearTables()

  @Test
  fun `Write_Should_Not_Block_Read`() {
    ops.`Write_Should_Not_Block_Read`()
  }
}
