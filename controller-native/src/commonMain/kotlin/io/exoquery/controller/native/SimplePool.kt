package io.exoquery.controller.native

import co.touchlab.sqliter.DatabaseConnection
import co.touchlab.sqliter.DatabaseManager
import co.touchlab.sqliter.Statement
import io.exoquery.controller.sqlite.*

sealed interface SqliterPoolType {
  data class SingleConnection(val db: DatabaseManager): SqliterPoolType
  data class MultiConnection(val db: DatabaseManager, val numReaders: Int): SqliterPoolType
  data class Wrapped(val conn: DatabaseConnection): SqliterPoolType
}

fun createConnection(type: SqliterPoolType, statementCacheCapacity: Int, isWritable: Boolean) =
  when (type) {
    is SqliterPoolType.MultiConnection -> {
      val conn = type.db.createMultiThreadedConnection()
      // NOTE: query_only=1 is not actually used for WAL mode to determine what can be simultaneous
      // and it is not needed to attain concurrent read access.
      // If you use it doing Statement.resetStatement will throw an exception beccause the statement
      // could technically be used for a write operation.
      //if (!isWritable) conn.withStatement("PRAGMA query_only = 1") { execute() }
      SqliterSession(conn, statementCacheCapacity)
    }
    is SqliterPoolType.SingleConnection -> {
      val conn = type.db.createMultiThreadedConnection()
      // See note on query_only=1 above
      //if (!isWritable) conn.withStatement("PRAGMA query_only = 1") { execute() }
      SqliterSession(conn, statementCacheCapacity)
    }
    is SqliterPoolType.Wrapped ->
      SqliterSession(type.conn, statementCacheCapacity)
  }

class SqliterPool(type: SqliterPoolType, val statementCacheCapacity: Int):
  DoublePoolBase<StatementCachingSession<DatabaseConnection, Statement>, Unit>(
    when(type) {
      is SqliterPoolType.SingleConnection, is SqliterPoolType.Wrapped -> DoublePoolType.Single
      is SqliterPoolType.MultiConnection -> DoublePoolType.Multi(type.numReaders)
    },
    { createConnection(type, statementCacheCapacity, true) },
    { createConnection(type, statementCacheCapacity, false) },
    {}, {},
    { it.session.close() }
  )

class SqliterSession(conn: DatabaseConnection, statementCacheCapacity: Int):
  SqliteSession<DatabaseConnection, Statement>(conn, statementCacheCapacity, { conn.createStatement(it) }, { it.resetAndClear() }, { it.finalizeStatement() })