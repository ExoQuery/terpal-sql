package io.exoquery.sql.android

import androidx.test.core.app.ApplicationProvider
import io.exoquery.sql.*
import io.exoquery.sql.android.WalTestDatabase.databaseName
import io.exoquery.sql.android.WalTestDatabase.ctx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

//@RunWith(RobolectricTestRunner::class)
//class WalConcurrencySpec {
//  data class MiscTest(val id: Long, val value: String)
//
//  val ctx  by lazy {
//    // NOTE any stdio output here seems to be swallowed by Robolectric and/or the CI test runner
//    ShadowLog.stream = System.out
//    System.setProperty("robolectric.logging","stdout")
//    runBlocking {
//      TerpalAndroidContext.fromSchema(WalSchemaTerpal, databaseName, ApplicationProvider.getApplicationContext(), poolingMode = TerpalAndroidContext.PoolingMode.MultipleReaderWal(3))
//    }
//  }
//
//  private val ops get() = object {
//    fun clearTables(): Unit = runBlocking {
//      ctx.runActions(
//        """
//      DELETE FROM MiscTest
//      """
//      )
//    }
//
//    suspend fun countRows(): Long =
//      Sql("SELECT count(*) FROM MiscTest").queryOf<Long>().runOn(ctx).first()
//
//
//
//    suspend fun insertTestData(testData: MiscTest) {
//      Sql("INSERT INTO MiscTest VALUES (${testData.id}, ${testData.value})").action().runOn(ctx)
//    }
//
//    fun `Write_Should_Not_Block_Read`() {
//      val counter = AtomicInteger(0)
//      val transactionStarted = AtomicInteger(0)
//
//      val block: suspend () -> Int = {
//        ctx.transaction {
//          insertTestData(MiscTest(1L, "arst 1"))
//
//
//          println("------------------------ Insert Done in Transaction: ${counter.get()}")
//          transactionStarted.incrementAndGet()
//          delay(3500)
//          println("------------------------ Transaction complete: ${counter.get()}")
//          counter.incrementAndGet()
//        }
//      }
//
//      runBlocking {
//        Sql("DELETE FROM MiscTest").queryOf<Long>().runOn(ctx)
//
//        withTimeout(100000) {
//          //assertEquals(0L, countRows(), "Initial row-count should be 0")
//
//          println("------------------------ Count rows initial: ${countRows()}")
//
//          val transactionJob = launch {
//            block()
//          }
//          // Transaction with write started but sleeping
//
//          while (transactionStarted.get() == 0) {
//            delay(100)
//          }
//
//          // Make sure the rows can be counted before the transaction completes
//          assertEquals(0, counter.get(), "Counter should be 0 (transaction not finished) - before read")
//          println("------------------------ Count rows - about to run")
//          val count = Sql("SELECT count(*) FROM MiscTest").queryOf<Long>().runOn(ctx).first()
//          println("------------------------ Count rows: ${count}")
//
//          //val ctx2 = TerpalAndroidContext.fromSchema(WalSchemaTerpal, databaseName, ApplicationProvider.getApplicationContext(), poolingMode = TerpalAndroidContext.PoolingMode.MultipleReaderWal(3))
//          //val cr2 = Sql("SELECT count(*) FROM MiscTest").queryOf<Long>().runOn(ctx2).first()
//          //println("------------------------ Count rows2: ${cr2}")
//
//          //assertEquals(0L, countRows(), "No rows should be present before transaction completes")
//          //assertEquals(0, counter.value, "Counter should be 0 (transaction not finished) - after read")
//
//          transactionJob.join()
//          assertEquals(counter.get(), 1)
//          assertEquals(transactionStarted.get(), 1)
//        }
//      }
//    }
//  }
//
//  @BeforeTest
//  fun clearTables() = ops.clearTables()
//
//  @Test
//  fun `Write Should Not Block Read`() =
//    ops.`Write_Should_Not_Block_Read`()
//}

@RunWith(RobolectricTestRunner::class)
class WalConcurrencySpec {
  val ops by lazy { WalConcurrencyOps(ctx) }

  @BeforeTest
  fun clearTables() = ops.clearTables()

  @Test
  fun `Write Should Not Block Read`() =
    ops.`Write_Should_Not_Block_Read`()
}
