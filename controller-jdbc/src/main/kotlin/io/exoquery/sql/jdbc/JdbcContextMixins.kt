package io.exoquery.sql.jdbc

import io.exoquery.sql.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext


typealias JdbcSqlEncoding = SqlEncoding<Connection, PreparedStatement, ResultSet>

object JdbcCoroutineContext: CoroutineContext.Key<CoroutineSession<Connection>> {}

interface HasTransactionalityJdbc: RequiresTransactionality<Connection, PreparedStatement>, HasSessionJdbc {
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


interface HasSessionJdbc: RequiresSession<Connection, PreparedStatement> {
  val database: DataSource

  override val sessionKey: CoroutineContext.Key<CoroutineSession<Connection>> get() = JdbcCoroutineContext
  override suspend open fun newSession(): Connection = database.connection
  override open fun closeSession(session: Connection): Unit = session.close()
  override open fun isClosedSession(session: Connection): Boolean = session.isClosed

  override open suspend fun <R> accessStmtReturning(sql: String, conn: Connection, returningColumns: List<String>, block: suspend (PreparedStatement) -> R): R {
    val stmt =
      if (returningColumns.isNotEmpty())
        conn.prepareStatement(sql, returningColumns.toTypedArray())
      else
        conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)

    return stmt.use { block(it) } // note stmt.use(block) doesn't work here because the inline signature of the `use` function doesn't allow it. Can use it in the anonymous-function form though
  }

  override open suspend fun <R> accessStmt(sql: String, conn: Connection, block: suspend (PreparedStatement) -> R): R =
    conn.prepareStatement(sql).use {
      block(it)
    }
}