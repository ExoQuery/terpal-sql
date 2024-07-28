package io.exoquery.sql.sqlite

import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

class TerpalLruStatementCache<Stmt>(val capacity: Int, val createStmt: (String) -> Stmt, val preparedFromCache: (Stmt) -> Unit, val evictStatement: (Stmt) -> Unit): StatementCache<Stmt> {
  // Note that since the only interaction with the LRU cache in the whole app here is via getOrCreate which has it's own
  // lock, any locks inside the LRU cache themselves are not needed. For the sake of performance optimization look
  // if removing them has any benifit.
  private val cacheRef by lazy {
    LruCache<String, Stmt>(capacity) { _, sql, stmt, _ -> evictStatement(stmt) }
  } // Don't create it in case the capacity is zero

  private val lock = reentrantLock()

  override fun getOrCreate(sql: String): Stmt =
    // if instructed to not cache anything just create the statement
    if (capacity == 0) {
      createStmt(sql)
    } else {
      // actually init the cache before the lock
      val cache = cacheRef

      lock.withLock {
        val stmt = cache.get(sql)
        if (stmt != null) {
          stmt
        } else {
          val newStmt = createStmt(sql)
          cache.put(sql, newStmt)
          preparedFromCache(newStmt)
          newStmt
        }
      }
    }
}