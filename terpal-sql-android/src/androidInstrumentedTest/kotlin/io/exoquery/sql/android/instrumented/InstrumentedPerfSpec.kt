package io.exoquery.sql.android.instrumented

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.exoquery.sql.PerfSchema
import io.exoquery.sql.WallPerformanceTest
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.junit.Test

@RunWith(AndroidJUnit4::class)
class InstrumentedPerfTest: InstrumentedSpec {
  // Performance/Stress test for WAL mode. Create a bunch of writers for random intervals and keep spinning up readers so long
  // as the writers are still writing. The readers will read from random intervals. Writers should not block readers, readers
  // should not block writers.

  @Test
  fun runTest() {
    val name = "perf_test.db"
    val basePath = "./"
    val maxRow = 100000
    val schema = PerfSchema(maxRow)
    runBlocking {
      val driver = createDriver(name, schema)
      println("------- Clearing Pref Table -------")
      driver.runRaw(schema.clearQuery)
      println("------- Reloading Pef Table Values -------")
      driver.runRaw(schema.loadQuery)
      WallPerformanceTest(driver, maxRow, 100, 10000).walPerfTest()
    }
  }
}
