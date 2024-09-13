package io.exoquery.sql.native

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseFileContext.deleteDatabase
import co.touchlab.sqliter.native.increment
import io.exoquery.sql.BasicSchema
import io.exoquery.sql.BasicSchemaTerpal
import io.exoquery.sql.Sql
import io.exoquery.sql.delight.runOnDriver
import io.exoquery.sql.sqlite.SimplePool
import io.exoquery.sql.sqlite.Waiter
import io.exoquery.sql.sqlite.getNumProcessorsOnPlatform
import io.exoquery.sql.waitRandom
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.concurrent.AtomicInt
import kotlin.test.Test
import kotlin.test.assertEquals

fun <T> runBlockingWithTimeout(timeout: Long, block: suspend CoroutineScope.() -> T): T =
  runBlocking {
    withTimeout(timeout) {
      block()
    }
  }

class PoolConcurrencySpec {

  class ContentionTest(val numSlots: Int, val waitMin: Int, val waitMax: Int, val totalTimeout: Long) {
    val marks = Array(numSlots) { AtomicInt(0) }
    val indexes = (0 until numSlots).toList().shuffled()

    fun run() {
      // create a simple pool in order to "borrow" things from it. In reality
      // we actually don't care about what's inside the pool for this test, just that
      // we can borrow and return things from it without deadlocking.
      val pool = SimplePool<String>(getNumProcessorsOnPlatform(), { "Connection: ${it.numEntries}" }, {})
      runBlockingWithTimeout(totalTimeout) {
        indexes.withIndex().forEach { (count, i) ->
          launch {
            // Wait randomly for each slot to simulate a real-world scenario
            waitRandom(waitMin, waitMax)
            //println("Task: ${i} trying to borrow connection")
            val connNum = pool.borrow()
            marks[i].value = 1
            waitRandom(waitMin, waitMax)
            //println("Finished task: ${count}->${i} with - ${connNum.value}")
            connNum.close()
          }
        }

        while (marks.any { it.value == 0 }) {
          delay(1000)
          println("Progress: ${marks.map { it.value }}")
        }
      }
    }
  }

  @Test
  fun `Pool Should be able to make progress when contended`() {
    //val test = ContentionTest(1000, 5, 10, 6000)
    val numProcs = getNumProcessorsOnPlatform()
    val test = ContentionTest(numProcs * 100, 5, 10,
      // If there are 4 processors there should be 400 slots, if each waits the max of 10ms the total
      // wait time should be less than 4000ms. Give it an upper bound of twice that of this test.
      (numProcs * 2000).toLong()
    )

  }

  @Test
  fun waiterSpec() {
    val counter = AtomicInt(0)
    val w = Waiter()
    runBlockingWithTimeout(3000) {
      launch {
        w.doWait()
        counter.increment()
      }
      assertEquals(counter.value, 0, "Initial counter value was not 0")
      delay(1000)
      assertEquals(counter.value, 0, "After-delay counter value was not 0")
      w.doNotify()
      w.doNotify()
      w.doNotify()

      delay(5)
      assertEquals(counter.value, 1, "Final counter value was not 1")
    }
  }


  @Test
  fun `Pool should wait once all connections are exhausted`() {
    fun currTime() = run {
      val now = Clock.System.now()
      val localDateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
      localDateTime.time to now.toEpochMilliseconds()
    }

    val counter = atomic(0)
    val pool = SimplePool<String>(1, { kotlin.random.Random.nextInt().toString() }, {})
    runBlockingWithTimeout(4000) {
      val a = pool.borrow()
      println("A:" + counter.incrementAndGet())
      a.close()
      assertEquals(1, counter.value, "Counter should have been 1 after first borrow")
      val b = pool.borrow()
      println("B:" + counter.incrementAndGet())
      b.close()
      assertEquals(2, counter.value, "Counter should have been 2 after second borrow")
      val (startTime, startMillis) = currTime()
      val c = pool.borrow()
      println("C: " + counter.incrementAndGet() + "at: ${startTime}")
      launch {
        assertEquals(3, counter.value, "Counter should have been 3 after third borrom (in top launch)")
        val (endTime, endMillis) = currTime()
        println("Call Delay at: ${endTime}")
        delay(1000)
        counter.getAndIncrement() // should be 4 after this
        assertEquals(4, counter.value, "Counter shuold have been for after top-launch increment")
        c.close()
      }
      launch {
        assertEquals(3, counter.value, "Counter should have been 3 after third borrom (in bottom launch)")
        val d = pool.borrow()
        val (endTime, endMillis) = currTime()
        println("D borrowed at: ${endTime}")
        // When the pool connection is returned the value shuold have been incremented once again
        assertEquals(4, counter.value, "Counter should have been 4 after bottom-launch wait ending")
      }
    }
  }

  @Test
  fun basicDriver() {
    val name = "testdb"
    deleteDatabase(name)
    val query = Sql("SELECT * from person").queryOf<Person>()
    val nativeDriver = NativeSqliteDriver(BasicSchema, name)

    println("reading list: " + query.runOnDriver(nativeDriver))
    println("insert new records: " + Sql("INSERT INTO person (id, firstName, lastName, age) VALUES (3,'Abraham', 'Avinu', 123);").action().runOnDriver(nativeDriver).value)
  }
}

@Serializable
data class Person(val id: Int, val firstName: String, val lastName: String, val age: Int)

