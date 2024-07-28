package io.exoquery.sql

import io.exoquery.sql.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.fail

class WallPerformanceTest<Session, Stmt>(val driver: ContextBase<Session, Stmt>, val maxRow: Int = 100000, val minIntervalSize: Int = 100, val maxIntervalSize: Int = 1000) {
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
      val end = min(start + Random.nextInt(minIntervalSize, maxIntervalSize), maxRow)
      val newInterval = UpdateInterval(start, end)
      //println("----- Adding interval: ${newInterval} ------")
      intervals.add(newInterval)
      currRow = end
    }
    return intervals.toTypedArray()
  }

  fun readerQuery(idMin: Int, idMax: Int) = "SELECT id, name, age FROM perf WHERE id >= $idMin AND id <= $idMax".toQuery<Perf>()
  //fun readerQuery(idMin: Int, idMax: Int) = "SELECT id, name, age FROM perf WHERE id == $idMin".toQuery<Perf>()
  fun writerQuery(id: Int) = "UPDATE perf SET age = age + 1 WHERE id = $id".toAction()

  // Performance/Stress test for WAL mode. Create a bunch of writers for random intervals and keep spinning up readers so long
  // as the writers are still writing. The readers will read from random intervals. Writers should not block readers, readers
  // should not block writers.
  fun walPerfTest() {
    val intervalList = run {
        val intervalsTemp = generateIntervals(maxRow)
        println("--------------- Intervals ---------------\n" + intervalsTemp.joinToString("\n"))

        intervalsTemp.shuffle()
        intervalsTemp.toList()
    }
    val writeIntervals = kotlin.collections.ArrayDeque(intervalList)

    val readIntervals = generateIntervals(maxRow)
    fun <T> Array<T>.getRandom(): T =
      this.get(Random.nextInt(0, this.size))
    // keep the readers reading so long as the writers are writing
    val readCount = atomic(0)

    val writersFinished = atomic(0)

    runBlocking {
      // At this point the database will be recreated

      while (writeIntervals.isNotEmpty()) {
        val interval = writeIntervals.removeFirst()
        // since we are counting down the intervals, spin up all the tasks immediately (i.e. wait inside the launch)
        launch {
          waitRandom(1, 10)
          val (result, currFinished) = driver.transaction {
            var out: List<Perf> = listOf()
            for (i in interval.start..interval.end) {
              driver.run(writerQuery(i))
              out = driver.run(readerQuery(i, i))
            }
            out to writersFinished.incrementAndGet()
          }
          println("----- Writer Finished Interval: ${interval.start}-${interval.end} - ${result} (finished: ${currFinished}/${intervalList.size}) ------")
        }
      }


      while (writersFinished.value < intervalList.size) {
        // choose an random interval to read from and read from there
        val interval = readIntervals.getRandom()
        // Since this will go on until readers are done, wait outside of the launch (otherwise launches will block the CPU!)
        waitRandom(10, 100)
        launch {
          val result = driver.run(readerQuery(interval.start, interval.end))
          //if (readCount.getAndIncrement() % readLogInterval == 0)
          println("----- Read #${readCount.getAndIncrement()} Query: ${interval.start}-${interval.end} - ${result.firstOrNull()} ------")
        }
      }

      readerQuery(1, maxRow).let {
        val modifiedRows = driver.stream(it).filter { it.age == 1 }.count()
        if (modifiedRows == maxRow) {
          println("----- All rows modified successfully ------")
        } else {
          fail("----- Not all rows modified. ${modifiedRows}/${maxRow} were modified. ------")
        }
      }

    }
  }

}