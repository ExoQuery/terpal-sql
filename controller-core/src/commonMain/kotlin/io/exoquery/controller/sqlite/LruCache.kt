package io.exoquery.controller.sqlite

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

class LruCache<K, V>(maxSize: Int, val onRemove: (Boolean, K, V, V?) -> Unit) {
  private val map: LinkedHashMap<K, V>

  /** Size of this cache in units. Not necessarily the number of elements.  */
  private var size = 0
  private var maxSize: Int

  private var putCount = 0
  private var createCount = 0
  private var evictionCount = 0
  private var hitCount = 0
  private var missCount = 0

  private val lock = reentrantLock() //FakeLock() //ReentrantLock()

  /**
   * @param maxSize for caches that do not override [.sizeOf], this is
   * the maximum number of entries in the cache. For all other caches,
   * this is the maximum sum of the sizes of the entries in this cache.
   */
  init {
    require(maxSize > 0) { "maxSize <= 0" }
    this.maxSize = maxSize
    this.map = LinkedHashMap<K, V>(0, 0.75f)
  }

  /**
   * Sets the size of the cache.
   *
   * @param maxSize The new maximum size.
   */
  fun resize(maxSize: Int) {
    require(maxSize > 0) { "maxSize <= 0" }

    lock.withLock {
      this.maxSize = maxSize
    }
    trimToSize(maxSize)
  }

  /**
   * Returns the value for `key` if it exists in the cache or can be
   * created by `#create`. If a value was returned, it is moved to the
   * head of the queue. This returns null if a value is not cached and cannot
   * be created.
   */
  fun get(key: K): V? {
    if (key == null) {
      throw NullPointerException("key == null")
    }

    var mapValue: V? = null
    lock.withLock {
      mapValue = map.get(key)
      if (mapValue != null) {
        hitCount++
        return mapValue
      }
      missCount++
    }

    /*
         * Attempt to create a value. This may take a long time, and the map
         * may be different when create() returns. If a conflicting value was
         * added to the map while create() was working, we leave that value in
         * the map and release the created value.
         */
    val createdValue: V = create(key) ?: return null

    lock.withLock {
      createCount++
      val mapValue = map.put(key, createdValue)
      if (mapValue != null) {
        // There was a conflict so undo that last put
        map.put(key, mapValue)
      } else {
        size += safeSizeOf(key, createdValue)
      }
    }

    if (mapValue != null) {
      entryRemoved(false, key, createdValue, mapValue)
      return mapValue
    } else {
      trimToSize(maxSize)
      return createdValue
    }
  }

  /**
   * Caches `value` for `key`. The value is moved to the head of
   * the queue.
   *
   * @return the previous value mapped by `key`.
   */
  fun put(key: K, value: V): V? {
    if (key == null || value == null) {
      throw NullPointerException("key == null || value == null")
    }


    val previous = lock.withLock {
      putCount++
      size += safeSizeOf(key, value)
      // create in this scope so the null check works correctly
      val previous = map.put(key, value)
      if (previous != null) {
        size -= safeSizeOf(key, previous)
      }
      previous
    }

    if (previous != null) {
      entryRemoved(false, key, previous, value)
    }

    trimToSize(maxSize)
    return previous
  }

  /**
   * Remove the eldest entries until the total of remaining entries is at or
   * below the requested size.
   *
   * @param maxSize the maximum size of the cache before returning. May be -1
   * to evict even 0-sized elements.
   */
  fun trimToSize(maxSize: Int) {
    while (true) {
      val keyValue = lock.withLock {
        check(!(size < 0 || (map.isEmpty() && size != 0))) {
          (this::class.simpleName
            + ".sizeOf() is reporting inconsistent results!")
        }
        if (size <= maxSize || map.isEmpty()) {
          null // don't remove the entry
        } else {
          val toEvict: Map.Entry<K, V> = map.entries.iterator().next()
          val key = toEvict.key
          val value = toEvict.value
          map.remove(key)
          size -= safeSizeOf(key, value)
          evictionCount++
          Pair(key, value)
        }
      }

      // If a key-value was actually returned then remove it, if it was not then we are done and exit from the function
      keyValue?.let { (key, value) ->
        entryRemoved(true, key, value, null)
      } ?: return
    }
  }

  /**
   * Removes the entry for `key` if it exists.
   *
   * @return the previous value mapped by `key`.
   */
  fun remove(key: K): V? {
    if (key == null) {
      throw NullPointerException("key == null")
    }

    var previous: V? = lock.withLock {
      val previous = map.remove(key)
      if (previous != null) {
        size -= safeSizeOf(key, previous)
      }
      previous
    }

    if (previous != null) {
      entryRemoved(false, key, previous, null)
    }

    return previous
  }

  /**
   * Called for entries that have been evicted or removed. This method is
   * invoked when a value is evicted to make space, removed by a call to
   * [.remove], or replaced by a call to [.put]. The default
   * implementation does nothing.
   *
   *
   * The method is called without synchronization: other threads may
   * access the cache while this method is executing.
   *
   * @param evicted true if the entry is being removed to make space, false
   * if the removal was caused by a [.put] or [.remove].
   * @param newValue the new value for `key`, if it exists. If non-null,
   * this removal was caused by a [.put]. Otherwise it was caused by
   * an eviction or a [.remove].
   */
  fun entryRemoved(evicted: Boolean, key: K, oldValue: V, newValue: V?, ) {
    onRemove(evicted, key, oldValue, newValue)
  }

  /**
   * Called after a cache miss to compute a value for the corresponding key.
   * Returns the computed value or null if no value can be computed. The
   * default implementation returns null.
   *
   *
   * The method is called without synchronization: other threads may
   * access the cache while this method is executing.
   *
   *
   * If a value for `key` exists in the cache when this method
   * returns, the created value will be released with [.entryRemoved]
   * and discarded. This can occur when multiple threads request the same key
   * at the same time (causing multiple values to be created), or when one
   * thread calls [.put] while another is creating a value for the same
   * key.
   */
  protected fun create(key: K): V? {
    return null
  }

  private fun safeSizeOf(key: K, value: V): Int {
    val result = sizeOf(key, value)
    check(result >= 0) { "Negative size: $key=$value" }
    return result
  }

  /**
   * Returns the size of the entry for `key` and `value` in
   * user-defined units.  The default implementation returns 1 so that size
   * is the number of entries and max size is the maximum number of entries.
   *
   *
   * An entry's size must not change while it is in the cache.
   */
  protected fun sizeOf(key: K, value: V): Int {
    return 1
  }

  /**
   * Clear the cache, calling [.entryRemoved] on each removed entry.
   */
  fun evictAll() {
    trimToSize(-1) // -1 will evict 0-sized elements
  }

  /**
   * For caches that do not override [.sizeOf], this returns the number
   * of entries in the cache. For all other caches, this returns the sum of
   * the sizes of the entries in this cache.
   */
  fun size(): Int  = lock.withLock {
    return size
  }

  /**
   * For caches that do not override [.sizeOf], this returns the maximum
   * number of entries in the cache. For all other caches, this returns the
   * maximum sum of the sizes of the entries in this cache.
   */
  fun maxSize(): Int  = lock.withLock {
    return maxSize
  }

  /**
   * Returns the number of times [.get] returned a value that was
   * already present in the cache.
   */
  fun hitCount(): Int  = lock.withLock {
    return hitCount
  }

  /**
   * Returns the number of times [.get] returned null or required a new
   * value to be created.
   */
  fun missCount(): Int = lock.withLock {
    return missCount
  }

  /**
   * Returns the number of times [.create] returned a value.
   */
  fun createCount(): Int  = lock.withLock {
    return createCount
  }

  /**
   * Returns the number of times [.put] was called.
   */
  fun putCount(): Int = lock.withLock {
    return putCount
  }

  /**
   * Returns the number of values that have been evicted.
   */
  fun evictionCount(): Int = lock.withLock {
    return evictionCount
  }

  /**
   * Returns a copy of the current contents of the cache, ordered from least
   * recently accessed to most recently accessed.
   */
  fun snapshot(): Map<K, V> = lock.withLock {
    return LinkedHashMap<K, V>(map)
  }

  override fun toString(): String {
    val accesses = hitCount + missCount
    val hitPercent = if (accesses != 0) (100 * hitCount / accesses) else 0
    return (
      "LruCache[maxSize=${maxSize},hits=${hitCount},misses=${missCount},hitRate=${hitPercent.toDouble()/100}]"
    )
  }
}
