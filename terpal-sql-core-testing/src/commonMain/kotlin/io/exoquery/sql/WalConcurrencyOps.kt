package io.exoquery.sql

import io.exoquery.controller.ControllerTransactional
import io.exoquery.controller.runActions
import io.exoquery.controller.runOn
import io.exoquery.controller.transaction
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.assertEquals

class WalConcurrencyOps<Session, Stmt, ExecutionOpts>(val ctx: ControllerTransactional<Session, Stmt, ExecutionOpts>) {

  fun clearTables(): Unit = runBlocking {
    ctx.runActions(
      """
      DELETE FROM MiscTest
      """
    )
  }

  suspend fun countRows(): Long =
    Sql("SELECT count(*) FROM MiscTest").queryOf<Long>().runOn(ctx).first()

  data class MiscTest(val id: Long, val value: String)

  fun `Write_Should_Not_Block_Read`() {
    val counter = atomic(0)
    val transactionStarted = atomic(0)

    val block: suspend () -> Int = {
      ctx.transaction {
        //println("------------------------ Insert Test Data in Transaction: ${counter.value} ------------------------")
        val testData = MiscTest(1L, "arst 1")
        Sql("INSERT INTO MiscTest VALUES (${testData.id}, ${testData.value})").action().run()

        //println("------------------------ Insert Done in Transaction: ${counter.value} ------------------------")
        transactionStarted.incrementAndGet()
        delay(3500)
        //println("------------------------ Transaction complete: ${counter.value} ------------------------")
        counter.incrementAndGet()
      }
    }

    runBlocking {
      Sql("DELETE FROM MiscTest").queryOf<Long>().runOn(ctx)

      withTimeout(20000) {
        //assertEquals(0L, countRows(), "Initial row-count should be 0")
        //println("------------------------ Count rows initial: ${countRows()} ------------------------")
        val transactionJob = launch {
          block()
        }
        // Transaction with write started but sleeping

        while (transactionStarted.value == 0) {
          delay(100)
        }

        // Make sure the rows can be counted before the transaction completes
        assertEquals(0, counter.value, "Counter should be 0 (transaction not finished) - before read")
        //println("------------------------ Count rows - about to run ------------------------")
        val count = countRows()
        //println("------------------------ Count rows: ${count} ------------------------")
        assertEquals(0L, count, "No rows should be present before transaction completes")
        assertEquals(0, counter.value, "Counter should be 0 (transaction not finished) - after read")

        transactionJob.join()
        assertEquals(counter.value, 1)
        assertEquals(transactionStarted.value, 1)
      }
    }
  }

}
