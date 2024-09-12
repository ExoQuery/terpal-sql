package io.exoquery.sql.sqlite

import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.channels.Channel
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.reentrantLock

interface DoublePoolType {
  /**
   * One reader, one writer i.e. connection I shared between all operations. Use this
   * for non-WAL mode since transactors need to lock everything so the best thing to do is only have one connection
   * and suspend everything that is waiting on the connection
   */
  data object Single: DoublePoolType
  /**
   * One reader, multiple writers i.e. connections are not shared between operations. Use this for WAL mode
   * There will be one writer but multiple readers, specify the number of them.
   */
  data class Multi(val numReaders: Int): DoublePoolType
  /**
   * If the platform supports multiple processors, use 1-writer, multiple readers. Otherwise, use a single
   * pool pool (with a single connection for readers and writers).
   */
  fun auto() =
    when (getNumProcessorsOnPlatform()) {
      1 -> Single
      else -> Multi(getNumProcessorsOnPlatform() - 1)
    }
}

class DoublePoolSession<Session>(protected val conn: Borrowed<Session>, val isWriter: Boolean) {
  fun close() = conn.close()
  fun isOpen() = conn.isOpen()
  val value get() = conn.value
}

expect fun getNumProcessorsOnPlatform():Int

/**
 * Since in Sqlite WAL mode you can create a MVCC-like behavior by allowing a single writer and multiple readers,
 * this pool is designed around this behavior. Users/subclassers of this pool can have either one connection for both
 * reads and writes (i.e. the non-WAL mode where both borrowWriter and borrowReader return the same connection)
 * or have a single reader and multiple writers. In the latter case, the number of readers should typically be
 * specified in the constructor.
 *
 * Note that the writer does not block the reader and the readers don't block the writer you should not
 * have any situations of starvation. In practice it seems that in situations in high fiber-contention
 * due to GC pauses a large group of readers being constantly spun up can cause indefinitely block
 * the coroutines. A possible future direction is to introduce reader timeouts or some other mechanism
 * to prevent overly aggressive readers.
 */
interface DoublePool<Session> {
  suspend fun borrowReader(): DoublePoolSession<Session>
  suspend fun borrowWriter(): DoublePoolSession<Session>

  fun finalize(): Unit
}

open class DoublePoolBase<Session>(
  val mode: DoublePoolType,
  protected val aquireWriter: (PoolContext) -> Session,
  protected val aquireReader: (PoolContext) -> Session,
  protected val finalize: (Session) -> Unit
): DoublePool<Session> {
  lateinit var readerPool: SimplePool<Session>
  lateinit var writerPool: SimplePool<Session>

  init {
    when(mode) {
      is DoublePoolType.Single -> {
        writerPool = SimplePool(1, aquireWriter, finalize)
        readerPool = writerPool

      }
      is DoublePoolType.Multi -> {
        readerPool = SimplePool(mode.numReaders, aquireWriter, finalize)
        writerPool = SimplePool(1, aquireWriter, finalize)
      }
    }
  }

  // Borrow the a reader, mark it as non-writable unless there is a single connection
  override suspend fun borrowReader(): DoublePoolSession<Session> =
    DoublePoolSession(readerPool.borrow(), if (mode == DoublePoolType.Single) true else false)
  override suspend fun borrowWriter(): DoublePoolSession<Session> =
    DoublePoolSession(writerPool.borrow(), true)

  override fun finalize() {
    when (mode) {
      is DoublePoolType.Single ->
        writerPool.finalize()
      is DoublePoolType.Multi -> {
        readerPool.finalize()
        writerPool.finalize()
      }
    }
  }
}


interface Borrowed<Session> {
  fun close()
  fun isOpen(): Boolean
  val value: Session

  companion object {
    // Simpler lifecycle, can only be open once and then when closed that's forever
    // delegates the actual release to pooled-entry which can be reused
    internal class BorrowedImpl<Session>(private val entry: PooledEntry<Session>): Borrowed<Session> {
      private val isAvailable: AtomicBoolean = atomic(true)
      override val value get() = entry.value
      override fun isOpen() = isAvailable.value
      override fun close() {
        // make sure that the marking of the availability of this is also par to fthe lock
        entry.poolLock.withLock {
          entry.release()
          // This borrowed instance is no longer available
          isAvailable.value = false
        }
      }
    }

    internal class BorrowedDummy<Session>(private val inputValue: Session): Borrowed<Session> {
      override fun close() {}
      override fun isOpen() = true
      override val value get() = inputValue
    }

    // This is the only way to create a borrowed instance
    fun <Session> fromPool(entry: PooledEntry<Session>): Borrowed<Session> = BorrowedImpl(entry)
    fun <Session> dummy(entry: Session): Borrowed<Session> = BorrowedDummy(entry)
  }
}



class PooledEntry<T>(val value: T, val orderNum: Int, internal val poolLock: ReentrantLock, private val waiter: Waiter) {
  val isAvailable = atomic(true)
  internal fun tryToAcquire(): Boolean = isAvailable.compareAndSet(expect = true, update = false)
  fun release() {
    val done = isAvailable.compareAndSet(expect = false, update = true)
    check(done)

    // Notes from Pool.kt
    // While signalling blocked threads does not require locking, doing so avoids a subtle race
    // condition in which:
    //
    // 1. a [loopForConditionalResult] iteration in [borrowEntry] slow path is happening concurrently;
    // 2. the iteration fails to see the atomic `isAvailable = true` above;
    // 3. we signal availability here but it is a no-op due to no waiting blocker; and finally
    // 4. the iteration entered an indefinite blocking wait, not being aware of us having signalled availability here.
    //
    // By acquiring the pool lock first, signalling cannot happen concurrently with the loop
    // iterations in [borrowEntry], thus eliminating the race condition.

    poolLock.withLock {
      waiter.doNotify()
    }
  }
}

inline class Waiter(private val channel: Channel<Unit> = Channel<Unit>(0)) {
  suspend fun doWait() { channel.receive() }
  fun doNotify() { channel.trySend(Unit) }
}

data class PoolContext(val numEntries: Int)

// Remove generic. This pool is only used for Sqliter connections.
class SimplePool<T>(val capacity: Int, val aquire: (PoolContext) -> T, val finalize: (T) -> Unit) {
  private val connsRef = atomic<List<PooledEntry<T>>?>(listOf())
  private val poolLock = reentrantLock()
  private val waiter = Waiter()

  suspend fun borrow(): Borrowed<T> {
    val nextAvailable = poolLock.withLock {
      // Reload the list since it could've been updated by other threads concurrently.
      val currConnections = connsRef.value ?: throw RuntimeException("pool closed")
      val currConnectionsNum = currConnections.count()

      if (currConnectionsNum < capacity) {
        // Capacity hasn't been reached — create a new entry to serve this call.
        val rawConn = aquire(PoolContext(currConnectionsNum))
        val conn = PooledEntry(rawConn, currConnectionsNum + 1, poolLock, waiter)
        val aquired = conn.tryToAcquire()
        check(aquired)

        connsRef.value = (currConnections + listOf(conn))
        conn
      } else {
        val connections = connsRef.value ?: throw RuntimeException("pool closed")
        var firstConn = connections.firstOrNull { it.tryToAcquire() }
        while (firstConn == null) {
          waiter.doWait()
          val connections = connsRef.value ?: throw RuntimeException("pool closed")
          firstConn = connections.firstOrNull { it.tryToAcquire() }
        }
        firstConn
      }
    }

    return Borrowed.fromPool(nextAvailable)
  }

  fun finalize(): Unit = poolLock.withLock {
    connsRef.value?.forEach { finalize(it.value) }
    connsRef.value = null
  }
}
