package io.exoquery.sql.android.instrumented

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.exoquery.sql.PerfSchema
import io.exoquery.sql.WallPerformanceTest
import io.exoquery.sql.android.TerpalAndroidDriver
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.junit.Test
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class InstrumentedPerfTest: InstrumentedSpec {
  // Performance/Stress test for WAL mode. Create a bunch of writers for random intervals and keep spinning up readers so long
  // as the writers are still writing. The readers will read from random intervals. Writers should not block readers, readers
  // should not block writers.

  data class TestConfig(val maxReaders: Int?, val poolingMode: TerpalAndroidDriver.PoolingMode)

  @Test
  fun runTest() {
    val name = "perf_test.db"
    val basePath = "./"
    val maxRow = 100000
    val schema = PerfSchema(maxRow)
    val messages = mutableListOf<String>()
    // You can use a seed random to ensure reproducibility

    fun runTest(conf: TestConfig) =
      runBlocking {
        val random = Random(675398276796102078L)

        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val poolingMode = conf.poolingMode
        val driver = TerpalAndroidDriver.fromApplicationContext(name, appContext, AndroidSqliteDriver.Callback(schema), poolingMode = poolingMode)

        println("------- Clearing Pref Table -------")
        driver.runRaw(schema.clearQuery)
        println("------- Reloading Pef Table Values -------")
        driver.runRaw(schema.loadQuery)
        messages.add(
          "[====== WAL Performance Test: ${conf} ======]" +
            WallPerformanceTest(driver, maxRow, 100, 1000, rand = random, maxReaders = conf.maxReaders).walPerfTest()
        )
        println("------------ Done with WAL Performance Test ------------")
      }

    listOf(
      TestConfig(100, TerpalAndroidDriver.PoolingMode.SingleSessionWal),
      TestConfig(100, TerpalAndroidDriver.PoolingMode.MultipleReaderWal(1)),
      TestConfig(100, TerpalAndroidDriver.PoolingMode.MultipleReaderWal(3)),
      TestConfig(100, TerpalAndroidDriver.PoolingMode.MultipleReaderWal(5)),
      TestConfig(100, TerpalAndroidDriver.PoolingMode.MultipleReaderWal(8)),
      TestConfig(100, TerpalAndroidDriver.PoolingMode.MultipleReaderWal(10)),
      TestConfig(1000, TerpalAndroidDriver.PoolingMode.SingleSessionWal),
      TestConfig(1000, TerpalAndroidDriver.PoolingMode.MultipleReaderWal(1)),
      TestConfig(1000, TerpalAndroidDriver.PoolingMode.MultipleReaderWal(3)),
      TestConfig(1000, TerpalAndroidDriver.PoolingMode.MultipleReaderWal(5)),
      TestConfig(1000, TerpalAndroidDriver.PoolingMode.MultipleReaderWal(8)),
      TestConfig(1000, TerpalAndroidDriver.PoolingMode.MultipleReaderWal(10)),
      TestConfig(null, TerpalAndroidDriver.PoolingMode.SingleSessionWal),
      TestConfig(null, TerpalAndroidDriver.PoolingMode.MultipleReaderWal(1)),
      TestConfig(null, TerpalAndroidDriver.PoolingMode.MultipleReaderWal(3)),
      TestConfig(null, TerpalAndroidDriver.PoolingMode.MultipleReaderWal(5)),
      TestConfig(null, TerpalAndroidDriver.PoolingMode.MultipleReaderWal(8)),
      TestConfig(null, TerpalAndroidDriver.PoolingMode.MultipleReaderWal(10))
    ).forEach(::runTest)

    println("------------- Done with all tests --------------")
    messages.forEach { println(it) }
  }
}
