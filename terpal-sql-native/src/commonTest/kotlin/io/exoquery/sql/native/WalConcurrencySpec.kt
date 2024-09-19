package io.exoquery.sql.native

import io.exoquery.sql.Sql
import io.exoquery.sql.native.WalTestDatabase.ctx
import io.exoquery.sql.runOn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.AtomicInt
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WalConcurrencySpec {
  @BeforeTest
  fun clearTables() {
    ctx.runRaw(
      """
      DELETE FROM MiscTest;
      """
    )
  }

  suspend fun countRows(): Long =
    Sql("SELECT count(*) FROM MiscTest").queryOf<Long>().runOn(ctx).first()

  data class MiscTest(val id: Long, val value: String)

  suspend fun insertTestData(testData: MiscTest) {
    Sql("INSERT INTO MiscTest VALUES (${testData.id}, ${testData.value})").action().runOn(ctx)
  }

  // TODO should have a test like this for android
  @Test
  fun `Write Should Not Block Read`() {
    val counter = AtomicInt(0)
    val transactionStarted = AtomicInt(0)

    val block: suspend () -> Int = {
      ctx.transaction {
        insertTestData(MiscTest(1L, "arst 1"))
        transactionStarted.incrementAndGet()
        delay(3500)
        counter.incrementAndGet()
      }
    }

    runBlocking {
      Sql("DELETE FROM MiscTest").queryOf<Long>().runOn(ctx)

      withTimeout(10000) {
        val transactionJob = launch {
          block()
        }
        // Transaction with write started but sleeping

        while (transactionStarted.value == 0) {
          delay(100)
        }

        // Make sure the rows can be counted before the transaction completes
        assertEquals(0, counter.value, "Counter should be 0 (transaction not finished) - before read")
        assertEquals(0L, countRows(), "No rows should be present before transaction completes")
        assertEquals(0, counter.value, "Counter should be 0 (transaction not finished) - after read")

        transactionJob.join()
        assertEquals(counter.value, 1)
        assertEquals(transactionStarted.value, 1)
      }
    }
  }

}