package io.exoquery.sql.native

import app.cash.sqldelight.internal.currentThreadId
import io.exoquery.sql.encodingdata.shouldBe
import io.exoquery.controller.sqlite.LruCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class LruCacheSpec {

  data class RemoveData(val wasEvicted: Boolean, val key: String, val oldValue: String, val newValue: String?)

  fun createCacheAndRemovals(cacheSize: Int): Pair<LruCache<String, String>, MutableList<RemoveData>> {
    val removals = mutableListOf<RemoveData>()
    val cache = LruCache<String, String>(cacheSize) { wasEvicted, key, oldValue, newValue -> removals.add(RemoveData(wasEvicted, key, oldValue, newValue)) }
    return Pair(cache, removals)
  }

  @Test
  fun testSimple() {
    val (cache, removals) = createCacheAndRemovals(2)
    cache.put("a", "1")
    cache.put("b", "2")
    cache.put("c", "3")
    cache.get("a") shouldBe null
    cache.get("b") shouldBe "2"
    cache.get("c") shouldBe "3"
    removals shouldBe listOf(RemoveData(true, "a", "1", null))
  }

  @Test
  fun testEviction() {
    val (cache, removals) = createCacheAndRemovals(3)
    cache.put("a", "1")
    cache.put("b", "2")
    cache.put("a", "11")
    cache.put("c", "3")
    cache.get("a") shouldBe "11"
    cache.get("b") shouldBe "2"
    cache.get("c") shouldBe "3"
    cache.get("other") shouldBe null
    // Note that should be false because the value was updated, not evicted
    removals shouldBe listOf(RemoveData(false, "a", "1", "11"))
  }


  @Test
  fun testLRUCacheEvictionInMultithreadedCode(): Unit = runBlocking {
    val cacheSize = 100
    val (lruCache, removeList) = createCacheAndRemovals(cacheSize)
    val countDownLatch = CompletableDeferred<Unit>()

    // Launch multiple coroutines to simulate concurrent cache operations
    val jobs = (0 until cacheSize).map { key ->
      val newKey = "value$key"
      launch {
        //println("Thread:[${currentThreadId()}], Key=[$newKey], Val=[$key]")
        lruCache.put(newKey, "$key")
      }
    }

    // Wait for all coroutines to finish
    jobs.forEach { it.join() }

    // Ensure that all keys inserted can be retrieved
    (0 until cacheSize).forEach { i ->
      val expectedValue = lruCache.get("value$i")
      assertEquals("$i", expectedValue, "Expected $i but got $expectedValue for key value$i")
    }

    countDownLatch.complete(Unit) // Signal that the test has finished
  }

}