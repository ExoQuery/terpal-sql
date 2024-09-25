package io.exoquery.sql.native

import co.touchlab.sqliter.DatabaseFileContext.deleteDatabase
import io.exoquery.sql.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Ignore
import kotlin.test.Test

class PerfTest {
  // Performance/Stress test for WAL mode. Create a bunch of writers for random intervals and keep spinning up readers so long
  // as the writers are still writing. The readers will read from random intervals. Writers should not block readers, readers
  // should not block writers.
  @Ignore
  @Test
  fun walPerfTest() {
    val name = "perf_test.db"
    val basePath = "./"
    val maxRow = 100000
    val schema = PerfSchema(maxRow)
    deleteDatabase(name, basePath)
    runBlocking {
      val driver = TerpalNativeContext.fromSchema(schema, name, basePath, TerpalNativeContext.PoolingMode.Multiple(10))
      driver.runRaw(schema.clearQuery)
      driver.runRaw(schema.loadQuery)
      WallPerformanceTest(driver, maxRow).walPerfTest()
    }
  }
}