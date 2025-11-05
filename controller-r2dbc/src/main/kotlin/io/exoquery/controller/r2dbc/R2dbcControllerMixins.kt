package io.exoquery.controller.r2dbc

import io.exoquery.controller.ControllerError
import io.exoquery.controller.CoroutineSession
import io.exoquery.controller.RequiresSession
import io.exoquery.controller.RequiresTransactionality
import io.exoquery.controller.jdbc.CoroutineTransaction
import io.r2dbc.spi.Connection
import io.r2dbc.spi.Row
import io.r2dbc.spi.Statement
import io.r2dbc.spi.ValidationDepth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

object R2dbcCoroutineContext: CoroutineContext.Key<CoroutineSession<Connection>> {}

interface HasTransactionalityR2dbc: RequiresTransactionality<Connection, Statement, R2dbcExecutionOptions>, HasSessionR2dbc {
  override val sessionKey: CoroutineContext.Key<CoroutineSession<Connection>> get() = R2dbcCoroutineContext

  override suspend fun <T> runTransactionally(block: suspend CoroutineScope.() -> T): T {
    val session = coroutineContext[sessionKey]?.session ?: error("No connection found")
    session.runWithManualCommit {
      val transaction = CoroutineTransaction()
      try {
        val result = withContext(transaction) { block() }
        commitTransaction()
        return result
      } catch (ex: Throwable) {
        rollbackTransaction()
        throw ex
      } finally {
        transaction.complete()
      }
    }
  }
}

internal inline fun <T> Connection.runWithManualCommit(block: Connection.() -> T): T {
  val before = this.isAutoCommit

  return try {
    this.setAutoCommit(false)
    this.run(block)
  } finally {
    this.setAutoCommit(before)
  }
}

interface HasSessionR2dbc: RequiresSession<Connection, Statement, R2dbcExecutionOptions> {
  override val sessionKey: CoroutineContext.Key<CoroutineSession<Connection>> get() = R2dbcCoroutineContext
  val connectionFactory: io.r2dbc.spi.ConnectionFactory

  override suspend fun newSession(executionOptions: R2dbcExecutionOptions): Connection =
    connectionFactory.create().awaitFirstOrNull() ?: error("Failed to create R2DBC connection")

  override suspend fun closeSession(session: Connection) =
    session.close().awaitFirstOrNull().run { Unit }

  override suspend fun isClosedSession(session: Connection): Boolean =
    session.validate(ValidationDepth.REMOTE).awaitFirstOrNull()?.let { it == false } ?: true // if null returned treat as closed

  override suspend fun <R> accessStmt(sql: String, conn: Connection, block: suspend (Statement) -> R): R =
    try {
      block(conn.createStatement(sql))
    } catch (ex: Throwable) {
      throw ControllerError("Error preparing statement: $sql", ex)
    }

  override suspend fun <R> accessStmtReturning(sql: String, conn: Connection, options: R2dbcExecutionOptions, returningColumns: List<String>, block: suspend (Statement) -> R): R =
    conn.createStatement(sql).let {
      val preparedWithColumns =
        if (returningColumns.isNotEmpty()) {
          it.returnGeneratedValues(*returningColumns.toTypedArray())
        } else {
          it
        }

      val fetchSize = options.fetchSize
      val preparedWithOptions =
        (fetchSize?.let { preparedWithColumns.fetchSize(it) } ?: preparedWithColumns)

      block(preparedWithOptions)
    }

}
