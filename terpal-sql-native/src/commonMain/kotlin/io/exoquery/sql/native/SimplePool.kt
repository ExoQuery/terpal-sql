package io.exoquery.sql.native

import co.touchlab.sqliter.DatabaseConnection
import co.touchlab.sqliter.DatabaseManager
import co.touchlab.sqliter.Statement
import co.touchlab.sqliter.withStatement
import io.exoquery.sql.sqlite.*

sealed interface SqliterPoolType {
  data class SingleConnection(val db: DatabaseManager): SqliterPoolType
  data class MultiConnection(val db: DatabaseManager, val numReaders: Int): SqliterPoolType
  data class Wrapped(val conn: DatabaseConnection): SqliterPoolType
}

fun createConnection(type: SqliterPoolType, statementCacheCapacity: Int, isWritable: Boolean) =
  when (type) {
    is SqliterPoolType.MultiConnection -> {
      val conn = type.db.createMultiThreadedConnection()
      if (!isWritable) conn.withStatement("PRAGMA query_only = 1") { execute() }
      SqliterSession(conn, statementCacheCapacity)
    }
    is SqliterPoolType.SingleConnection -> {
      val conn = type.db.createMultiThreadedConnection()
      if (!isWritable) conn.withStatement("PRAGMA query_only = 1") { execute() }
      SqliterSession(conn, statementCacheCapacity)
    }
    is SqliterPoolType.Wrapped ->
      SqliterSession(type.conn, statementCacheCapacity)
  }

class SqliterPool(type: SqliterPoolType, val statementCacheCapacity: Int):
  DoublePoolBase<StatementCachingSession<DatabaseConnection, Statement>>(
    when(type) {
      is SqliterPoolType.SingleConnection, is SqliterPoolType.Wrapped -> DoublePoolType.Single
      is SqliterPoolType.MultiConnection -> DoublePoolType.Multi(type.numReaders)
    },
    { createConnection(type, statementCacheCapacity, true) },
    { createConnection(type, statementCacheCapacity, false) },
    { it.session.close() }
  )

class SqliterSession(conn: DatabaseConnection, statementCacheCapacity: Int):
  SqliteSession<DatabaseConnection, Statement>(conn, statementCacheCapacity, { conn.createStatement(it) }, { it.resetAndClear() }, { it.finalizeStatement() })