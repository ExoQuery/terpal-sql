package io.exoquery.sql.android

import android.util.LruCache
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteStatement
import io.exoquery.sql.sqlite.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

sealed interface OpenHelper {
  val writableDatabase: SupportSQLiteDatabase
  val readableDatabase: SupportSQLiteDatabase
  fun close(): Unit
}
data class RealHelper(val value: SupportSQLiteOpenHelper): OpenHelper {
  override val writableDatabase get() = value.writableDatabase
  override val readableDatabase get() = value.readableDatabase
  override fun close() = value.close()
}
data object DummyHelper: OpenHelper {
  override val writableDatabase get() = throw UnsupportedOperationException("Dummy helper does not have a writeable database")
  override val readableDatabase get() = throw UnsupportedOperationException("Dummy helper does not have a readable database")
  override fun close() = Unit
}

typealias Connection = DoublePoolSession<StatementCachingSession<SupportSQLiteDatabase, SupportSQLiteStatement>, OpenHelper>
typealias AndroidDoublePool = DoublePool<StatementCachingSession<SupportSQLiteDatabase, SupportSQLiteStatement>, OpenHelper>
typealias AndroidDoublePoolBase = DoublePoolBase<StatementCachingSession<SupportSQLiteDatabase, SupportSQLiteStatement>, OpenHelper>

sealed interface AndroidPool: AndroidDoublePool {
  data class SingleConnection(val db: SupportSQLiteOpenHelper, val statementCacheCapacity: Int): AndroidPool, AndroidDoublePoolBase(
    DoublePoolType.Single,
    { AndroidSession(db.writableDatabase, statementCacheCapacity) },
    { AndroidSession(db.writableDatabase, statementCacheCapacity) },
    // For a pool with a single open-helper use that for all opens and closes and close the helper when the pool is closed
    { RealHelper(db) }, { db.close() },
    { it.session.close() }
  )
  data class MultiConnection(val makeHelper: () -> SupportSQLiteOpenHelper, val numReaders: Int, val statementCacheCapacity: Int): AndroidPool, AndroidDoublePoolBase(
    DoublePoolType.Multi(numReaders),
    { AndroidSession(it.context.writableDatabase, statementCacheCapacity) },
    // Despite the fact that technically .readableDatabase and .writableDatabase are the same thing and they access the same database,
    // since we are creating a pool with multiple instance of hte open helper, we want to make sure that anything using the sessions from the read-pool
    // do not accidentally write to the database which would break the MVCC conditions upon which SQLite's WAL mode creates.
    { AndroidSession(it.context.readableDatabase, statementCacheCapacity) },
    { RealHelper(makeHelper()) }, { it.close() },
    { it.session.close() }
  )
  data class Wrapped(val conn: SupportSQLiteDatabase, val statementCacheCapacity: Int): AndroidPool, AndroidDoublePoolBase(
    DoublePoolType.Single,
    { AndroidSession(conn, statementCacheCapacity) },
    { AndroidSession(conn, statementCacheCapacity) },
    { DummyHelper }, {},
    // If it's a single connection that is be shared, it's the responsibility of the caller to close it
    { Unit }
  )
  // In this mode, there is no concurrency gatekeeping over the connection at alll whatsoever. If two clients try to use the same connect
  // Use this for Android connections that are either not shared between operations or that already have built-in concurrency control
  data class WrappedUnsafe(val conn: SupportSQLiteDatabase, val statementCacheCapacity: Int): AndroidPool, AndroidDoublePool {
    override suspend fun borrowReader() = Connection(
      Borrowed.dummy(StatementCachingSession<SupportSQLiteDatabase, SupportSQLiteStatement>(conn, AndroidLruStatementCache(conn, statementCacheCapacity))),
      true
    )
    override suspend fun borrowWriter() = Connection(
      Borrowed.dummy(StatementCachingSession<SupportSQLiteDatabase, SupportSQLiteStatement>(conn, AndroidLruStatementCache(conn, statementCacheCapacity))),
      true
    )

    // Assuming this connection is shared with other reasources and has its own finalization logic
    override fun finalize() = Unit
  }
}

// Use android's built-in LRU cache instead of the only in terpal-core
class AndroidLruStatementCache(val conn: SupportSQLiteDatabase, val capacity: Int): StatementCache<SupportSQLiteStatement> {
  // Note that since the only interaction with the LRU cache in the whole app here is via getOrCreate which has it's own
  // lock, any locks inside the LRU cache themselves are not needed. For the sake of performance optimization look
  // if removing them has any benifit.
  private val cacheRef by lazy {
    object: LruCache<String, SupportSQLiteStatement>(capacity) {
      override fun entryRemoved(evicted: Boolean, key: String?, oldValue: SupportSQLiteStatement, newValue: SupportSQLiteStatement?) {
        oldValue.close()
      }
    }
  } // Don't create it in case the capacity is zero

  private val lock = ReentrantLock() // using the android lock instead of the atomicfu one here

  override fun getOrCreate(sql: String): SupportSQLiteStatement =
    // if instructed to not cache anything just create the statement
    if (capacity == 0) {
      conn.compileStatement(sql)
    } else {
      // actually init the cache before the lock
      val cache = cacheRef
      lock.withLock {
        val stmt = cache.get(sql)
        if (stmt != null) {
          stmt.clearBindings()
          stmt
        } else {
          val newStmt = conn.compileStatement(sql)
          cache.put(sql, newStmt)
          newStmt
        }
      }
    }
}

class AndroidSession(conn: SupportSQLiteDatabase, statementCacheCapacity: Int):
  SqliteSession<SupportSQLiteDatabase, SupportSQLiteStatement>(
    conn,
    statementCacheCapacity,
    { conn.compileStatement(it) },
    { it.clearBindings() },
    { it.close() },
    AndroidLruStatementCache(conn, statementCacheCapacity)
  )