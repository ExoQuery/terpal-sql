package io.exoquery.sql

import io.exoquery.controller.Action
import io.exoquery.controller.ControllerTransactional
import io.exoquery.controller.Query
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.fail
import kotlin.time.measureTimedValue

class WallPerformanceTest<Session, Stmt>(
  val driver: ControllerTransactional<Session, Stmt>,
  val maxRow: Int = 100000, val minIntervalSize: Int = 100,
  val maxIntervalSize: Int = 1000,
  val readWaitRange: Pair<Int, Int> = 10 to 100,
  val writeWaitRange: Pair<Int, Int> = 10 to 100,
  val rand: Random = Random.Default,
  val maxReaders: Int? = 100
) {
  inline fun <reified T> String.toQuery() = Query<T>(this, listOf(), serializer<T>())
  inline fun String.toAction() = Action(this, listOf())


  @Serializable
  data class Perf(val id: Int, val name: String, val age: Int)

  data class UpdateInterval(val start: Int, val end: Int)
  fun generateIntervals(maxRow: Int): Array<UpdateInterval> {
    val intervals = mutableListOf<UpdateInterval>()

    var currRow: Int = 0 // row number should actually start with 1 but it increments from previous row so this shuold start with 0
    while (currRow < maxRow) {
      val start = currRow + 1
      val end = min(start + rand.nextInt(minIntervalSize, maxIntervalSize), maxRow)
      val newInterval = UpdateInterval(start, end)
      //println("----- Adding interval: ${newInterval} ------")
      intervals.add(newInterval)
      currRow = end
    }
    return intervals.toTypedArray()
  }

  fun readerQuery(idMin: Int, idMax: Int) = "SELECT id, name, age FROM perf WHERE id >= $idMin AND id <= $idMax".toQuery<Perf>()

  val readerQueryFinal = "SELECT id, name, age FROM perf".toQuery<Perf>()
  //fun readerQuery(idMin: Int, idMax: Int) = "SELECT id, name, age FROM perf WHERE id == $idMin".toQuery<Perf>()
  fun writerQuery(id: Int) = "UPDATE perf SET age = age + 1 WHERE id = $id".toAction()

  // Performance/Stress test for WAL mode. Create a bunch of writers for random intervals and keep spinning up readers so long
  // as the writers are still writing. The readers will read from random intervals. Writers should not block readers, readers
  // should not block writers.
  fun walPerfTest(): String {

    val intervalList = run {
        val intervalsTemp = generateIntervals(maxRow)
        println("--------------- Intervals ---------------\n" + intervalsTemp.joinToString("\n"))

        intervalsTemp.shuffle(rand)
        intervalsTemp.toList()
    }
    val writeIntervals = kotlin.collections.ArrayDeque(intervalList)

    val readIntervals = generateIntervals(maxRow)
    fun <T> Array<T>.getRandom(): T =
      this.get(Random.nextInt(0, this.size))
    // keep the readers reading so long as the writers are writing
    val readCount = atomic(0)
    val activeReaders = atomic(0)

    val writersStarted = atomic(0)
    val writersFinished = atomic(0)

    return runBlocking {
      // At this point the database will be recreated

      suspend fun startRandomWriter()  = run {
        val writerNum = writersStarted.incrementAndGet()
        val interval = writeIntervals.removeFirst()
        launch {
          waitRandom(writeWaitRange.first, writeWaitRange.second, rand)
          println("----- Writer Start Interval (${writerNum}): ${interval.start}-${interval.end} ------")
          val (result, currFinished) = driver.transaction {
            var out: List<Perf> = listOf()
            for (i in interval.start..interval.end) {
              driver.run(writerQuery(i))
              out = driver.run(readerQuery(i, i)) //, "Writeback Reader (${writerNum}) ${i}"
            }
            out to writersFinished.incrementAndGet()
          }
          println("----- Writer Finished Interval  (${writerNum}): ${interval.start}-${interval.end} - ${result} (finished: ${currFinished}/${intervalList.size}) ------")
        }
      }

      suspend fun startRandomReader() = run {
        // choose an random interval to read from and read from there
        val interval = readIntervals.getRandom()
        // Since this will go on until readers are done, wait outside of the launch (otherwise launches will block the CPU!)
        //println("---------- Launching a Reader for an interval since ${writersFinished.value} < ${intervalList.size} ----------")
        launch {
          activeReaders.incrementAndGet()
          val readerNum = readCount.incrementAndGet()
          val result = driver.run(readerQuery(interval.start, interval.end))
          activeReaders.decrementAndGet()
          println("----- Reader Finished #${readerNum} Query: ${interval.start}-${interval.end} - ${result.firstOrNull()} ------")
        }
      }

      val timerMessage = atomic(true)
      val timerJob = launch {
        while (timerMessage.value) {
          delay(3000)
          println("----- Read #${readCount.value} (Active: ${activeReaders}/${maxReaders}) ------")
        }
      }


      val (kv, timeTaken) = measureTimedValue {
        val writeJobsLaunched = mutableListOf<Job>()
        coroutineScope {
          while (writeIntervals.isNotEmpty()) {
            writeJobsLaunched.add(startRandomWriter())
          }
        }

        val readJobsLaunched = mutableListOf<Job>()
        coroutineScope {
          while (writersFinished.value < intervalList.size) {
            waitRandom(readWaitRange.first, readWaitRange.second, rand)
            // If readers unconstrained, just keep launching them
            if (maxReaders == null)
              readJobsLaunched.add(startRandomReader())
            // If readers are constrained, only launch if we have room
            else if (activeReaders.value < maxReaders) {
              readJobsLaunched.add(startRandomReader())
            }
          }
        }

        writeJobsLaunched.joinAll()
        // wait for the write jobs to finish, number of readers we count as completed are the ones that were done right when we finished with the readers
        // If we wait for all of the joined readers to finish we will overcount because all of the readers that were blocked by the writers will also finish
        val readersFinishedDuringWrites = readCount.value
        // wait for all of the reader jobs to finish
        readersFinishedDuringWrites to readJobsLaunched
      }

      val (readersFinishedDuringWrites, readJobsLaunched) = kv
      readJobsLaunched.joinAll() // readers join needs to be outside of the measureTimedValue block otherwise we will be measuring the time after the writes are done and readers are still running
      timerJob.cancelAndJoin()
      timerMessage.value = false

      println("---------------- Calculating Results ----------------")

      val modifiedRows = driver.stream(readerQueryFinal).filter { it.age == 1 }.count()
      val stats = "(Writers:${intervalList.size}, Readers:${readersFinishedDuringWrites})"
      if (modifiedRows == maxRow) {
        val outputMsg = "----- All rows modified successfully in ${timeTaken} $stats ------"
        println(outputMsg)
        outputMsg
      } else {
        val outputMsg = "----- Not all rows modified. ${modifiedRows}/${maxRow} were modified in ${timeTaken} $stats. ------"
        fail(outputMsg)
      }
    }
  }

}