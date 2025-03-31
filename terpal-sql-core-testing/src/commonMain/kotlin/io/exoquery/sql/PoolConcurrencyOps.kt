package io.exoquery.sql

import io.exoquery.controller.sqlite.SimplePool
import io.exoquery.controller.sqlite.getNumProcessorsOnPlatform
import io.exoquery.controller.sqlite.Waiter
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.test.assertEquals

fun <T> runBlockingWithTimeout(timeout: Long, block: suspend CoroutineScope.() -> T): T =
  runBlocking {
    withTimeout(timeout) {
      block()
    }
  }

class PoolConcurrencyOps {

  class ContentionTest(val numSlots: Int, val waitMin: Int, val waitMax: Int, val totalTimeout: Long) {
    val marks = Array(numSlots) { atomic(0) }
    val indexes = (0 until numSlots).toList().shuffled()

    fun run() {
      // create a simple pool in order to "borrow" things from it. In reality
      // we actually don't care about what's inside the pool for this test, just that
      // we can borrow and return things from it without deadlocking.
      val pool = SimplePool<String, Unit>(getNumProcessorsOnPlatform(), { "Connection: ${it.numEntries}" }, {}, {}, {})
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

  fun `Pool_Should_be_able_to_make_progress_when_contended`() {
    //val test = ContentionTest(1000, 5, 10, 6000)
    val numProcs = getNumProcessorsOnPlatform()
    val test = ContentionTest(numProcs * 100, 5, 10,
      // If there are 4 processors there should be 400 slots, if each waits the max of 10ms the total
      // wait time should be less than 4000ms. Give it an upper bound of twice that of this test.
      (numProcs * 2000).toLong()
    )
  }

  fun `Pool_waiter_shuold_wait_until_notified`() {
    val counter = atomic(0)
    val w = Waiter()
    runBlockingWithTimeout(3000) {
      launch {
        w.doWait()
        counter.incrementAndGet()
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


  fun `Pool_should_wait_once_all_connections_are_exhausted`() {
    fun currTime() = run {
      val now = Clock.System.now()
      val localDateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
      localDateTime.time to now.toEpochMilliseconds()
    }

    val counter = atomic(0)
    val pool = SimplePool<String, Unit>(1, { kotlin.random.Random.nextInt().toString() }, {}, {}, {})
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
}

@Serializable
data class Person(val id: Int, val firstName: String, val lastName: String, val age: Int)

