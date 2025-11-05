package io.exoquery.controller.android

import androidx.sqlite.db.SupportSQLiteStatement
import io.exoquery.controller.CoroutineSession
import io.exoquery.controller.RequiresSession
import io.exoquery.controller.RequiresTransactionality
import io.exoquery.controller.jdbc.CoroutineTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

object AndroidCoroutineContext: CoroutineContext.Key<CoroutineSession<Connection>> {}

interface HasSessionAndroid: RequiresSession<Connection, SupportSQLiteStatement, UnusedOpts> {
  val pool: AndroidPool
  override val sessionKey: CoroutineContext.Key<CoroutineSession<Connection>> get() = AndroidCoroutineContext

  // This is the WRITER session
  // Use this for the transactor pool (that's what the RequiresTransactionality interface is for)
  // for reader connections we borrow readers
  override suspend fun newSession(options: UnusedOpts): Connection =
    prepareSession(pool.borrowWriter())

  fun prepareSession(session: Connection): Connection

  override suspend fun closeSession(session: Connection): Unit = session.close()
  override suspend fun isClosedSession(session: Connection): Boolean = !session.isOpen()

   override suspend fun <R> accessStmtReturning(
     sql: String,
     conn: Connection,
     options: UnusedOpts,
     returningColumns: List<String>,
     block: suspend (SupportSQLiteStatement) -> R
   ): R {
    val stmt = conn.value.createStatement(sql)
    return try {
      block(stmt)
    } finally {
      // Don't finalize the statement here because it could be reused from the pool, just clean it
      stmt.clearBindings()
    }
  }

  override suspend fun <R> accessStmt(sql: String, conn: Connection, block: suspend (SupportSQLiteStatement) -> R): R {
    val stmt = conn.value.createStatement(sql)
    return try {
      block(stmt)
    } finally {
      // Don't finalize the statement here because it could be reused from the pool, just clean it
      // (technically it should be cleared on the pool eviction function (i.e. once this connection is resused)
      // but I don't want to rely on that for now and it first here)
      stmt.clearBindings()
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

interface HasTransactionalityAndroid: RequiresTransactionality<Connection, SupportSQLiteStatement, UnusedOpts>, HasSessionAndroid {
  val walMode: WalMode

  // In RequiresTransactionality this is run inside of withConnection. Now withConnection is implemented here specifically as
  // having a WRITEABLE connection which means that in sqlite this will be in a pool of only 1. There are other
  // methods that only have a reader connection but they are not used here.
  override suspend fun <T> runTransactionally(block: suspend CoroutineScope.() -> T): T {
    val session = coroutineContext.get(sessionKey)?.session ?: error("No connection found")
    //println("------- Getting session from transactional run: ${session}")
    val transaction = CoroutineTransaction()
    try {

      when (walMode) {
        // When in WAL mode, we want readers to be able to read while the writer is writing
        WalMode.Enabled -> {
          //println("------- Beginning non-exclusive transaction")
          session.value.session.beginTransactionNonExclusive()
        }
        else -> {
          //println("------- Beginning transaction")
          session.value.session.beginTransaction()
        }
      }
      val result = withContext(transaction) { block() }
      // setting it successful makes it not rollback
      //println("------- Marking transaction successful")
      session.value.session.setTransactionSuccessful()
      return result
    } catch (ex: Throwable) {
      throw ex
    } finally {
      //println("------- Ending transaction")
      session.value.session.endTransaction()
      transaction.complete()
    }
  }
}
