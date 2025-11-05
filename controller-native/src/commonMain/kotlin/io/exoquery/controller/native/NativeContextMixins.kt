package io.exoquery.controller.native

import co.touchlab.sqliter.DatabaseConnection
import co.touchlab.sqliter.Statement
import io.exoquery.controller.*
import io.exoquery.controller.jdbc.CoroutineTransaction
import io.exoquery.controller.sqlite.DoublePoolSession
import io.exoquery.controller.sqlite.StatementCachingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

// I.e. a connection is a Sqliter session wrapped in a StatementCachingSession object (which caches the
// prepared-statements) pooled in a DoublePool which consists of a reader/writer pool references
// configured based on sqliter needs.
typealias Connection = DoublePoolSession<StatementCachingSession<DatabaseConnection, Statement>, Unit>

object NativeCoroutineContext: CoroutineContext.Key<CoroutineSession<Connection>> {}

interface HasSessionNative: RequiresSession<Connection, Statement, UnusedOpts> {
  val pool: SqliterPool
  override val sessionKey: CoroutineContext.Key<CoroutineSession<Connection>> get() = NativeCoroutineContext

  // This is the WRITER session
  // Use this for the transactor pool (that's what the RequiresTransactionality interface is for)
  // for reader connections we borrow readers
  override suspend fun newSession(options: UnusedOpts): Connection = pool.borrowWriter()
  override suspend fun closeSession(session: Connection): Unit = session.close()
  override suspend fun isClosedSession(session: Connection): Boolean = !session.isOpen()

  override suspend fun <R> accessStmtReturning(sql: String, conn: Connection, options: UnusedOpts, returningColumns: List<String>, block: suspend (Statement) -> R): R {
    val stmt = conn.value.createStatement(sql)
    return try {
      block(stmt)
    } finally {
      // Don't finalize the statement here because it could be reused from the pool, just clean it
      stmt.resetAndClear()
    }
  }

  override suspend fun <R> accessStmt(sql: String, conn: Connection, block: suspend (Statement) -> R): R {
    val stmt = conn.value.createStatement(sql)
    return try {
      block(stmt)
    } finally {
      // Don't finalize the statement here because it could be reused from the pool, just clean it
      // (technically it should be cleared on the pool eviction function (i.e. once this connection is resused)
      // but I don't want to rely on that for now and it first here)
      stmt.resetAndClear()
    }
  }

  // Need to override this and 'upgrade' the connection & get a writeable session if we don't have one
  // i.e. does it have an open WRITER connection.
  // TODO this is called by withConnection and will return false for a connection object that
  //      has a reader. Should we override withConnection here and immediately return the connection
  //      to the pool since that will borrow a write connection? Right now we are keeping it around
  //      in the coroutine context. Note that this scenario should not cause deadlock in a
  //      reader-needs-writer,writer-needs-reader scenario since the the coroutine that has
  //      the writer session will use it as the reader (see hasOpenReadOnlyConnection which
  //      doesn't care where the thing it has is a reader or writer).
  override suspend fun CoroutineContext.hasOpenConnection(): Boolean {
    val session = get(sessionKey)?.session
    return session != null && session.isWriter && !isClosedSession(session)
  }
}

interface HasTransactionalityNative: RequiresTransactionality<Connection, Statement, UnusedOpts>, HasSessionNative {
  // In RequiresTransactionality this is run inside of withConnection. Now withConnection is implemented here specifically as
  // having a WRITEABLE connection which means that in sqlite this will be in a pool of only 1. There are other
  // methods that only have a reader connection but they are not used here.
  override suspend fun <T> runTransactionally(block: suspend CoroutineScope.() -> T): T {
    val session = coroutineContext.get(sessionKey)?.session ?: error("No connection found")

    val transaction = CoroutineTransaction()
    try {
      //println("---------- Calling beginTransaction")
      session.value.session.beginTransaction()
      //println("---------- About to run block")
      val result = withContext(transaction) { block() }
      // setting it successful makes it not rollback
      //println("---------- Finished running block")
      session.value.session.setTransactionSuccessful()
      session.value.session.endTransaction()
      return result
    } catch (ex: Throwable) {
      session.value.session.endTransaction()
      throw ex
    } finally {
      transaction.complete()
    }
  }
}
