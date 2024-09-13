package io.exoquery.sql.android

import android.util.LruCache
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteStatement
import io.exoquery.sql.sqlite.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

sealed interface AndroidPool: DoublePool<StatementCachingSession<SupportSQLiteDatabase, SupportSQLiteStatement>> {
  data class SingleConnection(val db: SupportSQLiteOpenHelper, val statementCacheCapacity: Int): AndroidPool, DoublePoolBase<StatementCachingSession<SupportSQLiteDatabase, SupportSQLiteStatement>>(
    DoublePoolType.Single,
    { AndroidSession(db.writableDatabase, statementCacheCapacity) },
    { AndroidSession(db.writableDatabase, statementCacheCapacity) },
    { it.session.close() }
  )
  data class MultiConnection(val db: SupportSQLiteOpenHelper, val numReaders: Int, val statementCacheCapacity: Int): AndroidPool, DoublePoolBase<StatementCachingSession<SupportSQLiteDatabase, SupportSQLiteStatement>>(
    DoublePoolType.Multi(numReaders),
    { AndroidSession(db.writableDatabase, statementCacheCapacity) },
    //{ AndroidSession(db.writableDatabase, statementCacheCapacity) },
    { AndroidSession(db.readableDatabase, statementCacheCapacity) },
    { it.session.close() }
  )
  data class Wrapped(val conn: SupportSQLiteDatabase, val statementCacheCapacity: Int): AndroidPool, DoublePoolBase<StatementCachingSession<SupportSQLiteDatabase, SupportSQLiteStatement>>(
    DoublePoolType.Single,
    { AndroidSession(conn, statementCacheCapacity) },
    { AndroidSession(conn, statementCacheCapacity) },
    // If it's a single connection that is be shared, it's the responsibility of the caller to close it
    { Unit }
  )
  // In this mode, there is no concurrency gatekeeping over the connection at alll whatsoever. If two clients try to use the same connect
  // Use this for Android connections that are either not shared between operations or that already have built-in concurrency control
  data class WrappedUnsafe(val conn: SupportSQLiteDatabase, val statementCacheCapacity: Int): AndroidPool, DoublePool<StatementCachingSession<SupportSQLiteDatabase, SupportSQLiteStatement>> {
    override suspend fun borrowReader() = DoublePoolSession(
      Borrowed.dummy(StatementCachingSession<SupportSQLiteDatabase, SupportSQLiteStatement>(conn, AndroidLruStatementCache(conn, statementCacheCapacity))),
      true
    )
    override suspend fun borrowWriter() = DoublePoolSession(
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