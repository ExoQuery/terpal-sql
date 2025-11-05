package io.exoquery.controller.jdbc

import io.exoquery.controller.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext


typealias JdbcSqlEncoding = SqlEncoding<Connection, PreparedStatement, ResultSet>

object JdbcCoroutineContext: CoroutineContext.Key<CoroutineSession<Connection>> {}

interface HasTransactionalityJdbc: RequiresTransactionality<Connection, PreparedStatement, JdbcExecutionOptions>, HasSessionJdbc {
  override val sessionKey: CoroutineContext.Key<CoroutineSession<Connection>> get() = JdbcCoroutineContext

  override open suspend fun <T> runTransactionally(block: suspend CoroutineScope.() -> T): T {
    val session = coroutineContext.get(sessionKey)?.session ?: error("No connection found")
    session.runWithManualCommit {
      val transaction = CoroutineTransaction()
      try {
        val result = withContext(transaction) { block() }
        commit()
        return result
      } catch (ex: Throwable) {
        rollback()
        throw ex
      } finally {
        transaction.complete()
      }
    }
  }
}

internal inline fun <T> Connection.runWithManualCommit(block: Connection.() -> T): T {
  val before = autoCommit

  return try {
    autoCommit = false
    this.run(block)
  } finally {
    autoCommit = before
  }
}


interface HasSessionJdbc: RequiresSession<Connection, PreparedStatement, JdbcExecutionOptions> {
  val database: DataSource

  override val sessionKey: CoroutineContext.Key<CoroutineSession<Connection>> get() = JdbcCoroutineContext
  override suspend open fun newSession(options: JdbcExecutionOptions): Connection = run {
    val conn = options.prepareConnection(database.connection)
    val sessionTimeout = options.sessionTimeout
    if (sessionTimeout != null) {
      conn.setNetworkTimeout(Dispatchers.IO.asExecutor(), sessionTimeout)
    }
    conn
  }

  override open suspend fun closeSession(session: Connection): Unit = session.close()
  override open suspend fun isClosedSession(session: Connection): Boolean = session.isClosed

  override open suspend fun <R> accessStmtReturning(sql: String, conn: Connection, options: JdbcExecutionOptions, returningColumns: List<String>, block: suspend (PreparedStatement) -> R): R {
    val stmt =
      if (returningColumns.isNotEmpty())
        options.prepareStatement(conn.prepareStatement(sql, returningColumns.toTypedArray()))
      else
        options.prepareStatement(conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS))

    val fetchSize = options.fetchSize
    val queryTimeout = options.queryTimeout
    if (fetchSize != null) {
      stmt.fetchSize = fetchSize
    }
    if (queryTimeout != null) {
      stmt.queryTimeout = queryTimeout
    }

    return stmt.use { block(it) } // note stmt.use(block) doesn't work here because the inline signature of the `use` function doesn't allow it. Can use it in the anonymous-function form though
  }

  override open suspend fun <R> accessStmt(sql: String, conn: Connection, block: suspend (PreparedStatement) -> R): R =
    conn.prepareStatement(sql).use {
      block(it)
    }
}
