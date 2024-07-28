package io.exoquery.sql.android

import android.database.sqlite.SQLiteException
import android.os.Build
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteStatement
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import io.exoquery.sql.*
import io.exoquery.sql.sqlite.SqliteCursorWrapper
import io.exoquery.sql.sqlite.SqliteSqlEncoding
import io.exoquery.sql.sqlite.SqliteStatementWrapper
import io.exoquery.sql.sqlite.Unused
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.experimental.ExperimentalTypeInference

class TerpalAndroidContext internal constructor(
  override val encodingConfig: AndroidEncodingConfig,
  override val pool: AndroidPool,
  private val windowSizeBytes: Long? = null
): ContextBase<Connection, SupportSQLiteStatement>, java.io.Closeable,
  WithEncoding<Unused, SqliteStatementWrapper, SqliteCursorWrapper>,
  WithReadOnlyVerbs,
  HasTransactionalityAndroid {

  override val startingStatementIndex = StartingIndex.One
  override val startingResultRowIndex = StartingIndex.Zero

  sealed interface PoolingMode {
    object Single: PoolingMode
    data class Multiple(val numReaders: Int): PoolingMode
  }
  companion object {
    val DEFAULT_CACHE_CAPACITY = 5

    // TODO docs for how to use SqlDelight SqlSchema with callsbacks for schema migration
    fun fromApplicationContext(
      databaseName: String,
      context: android.content.Context,
      callback: SupportSQLiteOpenHelper.Callback,
      factory: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory(),
      poolingMode: PoolingMode = PoolingMode.Single,
      cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
      encodingConfig: AndroidEncodingConfig = AndroidEncodingConfig.Empty(),
      useNoBackupDirectory: Boolean = false,
      windowSizeBytes: Long? = null
      ): TerpalAndroidContext {
      val openHelper =
        factory.create(
          SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .noBackupDirectory(useNoBackupDirectory)
            .callback(callback)
            .build(),
        )

      return TerpalAndroidContext.fromOpenHelper(openHelper, poolingMode, cacheCapacity, encodingConfig, windowSizeBytes)
    }

    fun fromOpenHelper(
      openHelper: SupportSQLiteOpenHelper,
      poolingMode: PoolingMode = PoolingMode.Single,
      cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
      encodingConfig: AndroidEncodingConfig = AndroidEncodingConfig.Empty(),
      windowSizeBytes: Long? = null
    ): TerpalAndroidContext {
      val pool =
        when (poolingMode) {
          is PoolingMode.Single -> AndroidPool.SingleConnection(openHelper, cacheCapacity)
          is PoolingMode.Multiple -> AndroidPool.MultiConnection(openHelper, cacheCapacity, poolingMode.numReaders)
        }

      return TerpalAndroidContext(encodingConfig, pool, windowSizeBytes)
    }

    fun fromSingleSession(
      connection: SupportSQLiteDatabase,
      synchronizedAccess: Boolean,
      cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
      encodingConfig: AndroidEncodingConfig = AndroidEncodingConfig.Empty(),
      windowSizeBytes: Long? = null
    ): TerpalAndroidContext {
      val pool =
        if (synchronizedAccess)
          AndroidPool.Wrapped(connection, cacheCapacity)
        else
          AndroidPool.WrappedUnsafe(connection, cacheCapacity)

      return TerpalAndroidContext(encodingConfig, pool, windowSizeBytes)
    }
  }

  // Is there an open writer?
  override fun CoroutineContext.hasOpenConnection(): Boolean {
    val session = get(sessionKey)?.session
    //if (session != null)
    //  println("--------- (${currentThreadId()}) Found session: ${if (session.isWriter) "WRITER" else "JUST READER, needs promotion" } - isClosed: ${isClosedSession(session)}")
    //else
    //  println("---------- (${currentThreadId()}) No session fuond, create a new one!")
    return session != null && session.isWriter && !isClosedSession(session)
  }

  // For some reason we need to override this so the overridden hasOpenConnection (above) is called (I think)
  // that is very odd. Need to investigate more.
  override suspend fun <T> withConnection(block: suspend CoroutineScope.() -> T): T {
    return if (coroutineContext.hasOpenConnection()) {
      //println("----- (${currentThreadId()}) has open connection, running block")
      withContext(coroutineContext + Dispatchers.IO) { block() }
    } else {
      //println("----- (${currentThreadId()}) creating new session")
      val session = newSession()
      try {
        //println("----- (${currentThreadId()}) running block with new session")
        withContext(CoroutineSession(session, sessionKey) + Dispatchers.IO) { block() }
      } finally { closeSession(session) }
    }
  }

  override fun extractColumnInfo(row: SqliteCursorWrapper): List<ColumnInfo>? =
    when {
      // This context always uses the DelightCursorWrapper and always use sthe SqliterSqlCursor. If this is not the case it is probably an error.
      row is AndroidxCursorWrapper -> {
        val realRow = row.cursor
        (0..<realRow.columnCount).map { idx ->
          // TODO for getType get the row type name for sqlite (there should only be 5 actual types)
          ColumnInfo(realRow.getColumnName(idx), realRow.getType(idx).toString())
        }
      }
      else -> null
    }

  override suspend fun <R> accessStmt(sql: String, conn: Connection, block: suspend (SupportSQLiteStatement) -> R): R {
    if (sql.trim().isEmpty()) throw IllegalStateException("SQL statement is empty")
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

  private inline fun <T> tryCatchQuery(sql: String, op: () -> T): T =
    try {
      op()
    } catch (e: SQLiteException) {
      failSql("Error executing query: ${sql}", e)
    }

  suspend fun <T> FlowCollector<T>.emitResultSet(resultRow: AndroidxCursorWrapper, extract: (AndroidxCursorWrapper) -> T) {
    while (resultRow.next()) {
      emit(extract(resultRow))
    }
  }

  override val encodingApi = SqliteSqlEncoding

  protected fun wrap(stmt: SupportSQLiteStatement) = AndroidxStatementWrapper(stmt)

  suspend fun runActionScoped(sql: String, params: List<Param<*>>): Long =
    withConnection {
      val conn = localConnection()
      accessStmt(sql, conn) { stmt ->
        prepare(wrap(stmt), Unused, params)
        tryCatchQuery(sql) {
          if (sql.trim().lowercase().startsWith("insert")) {
            stmt.executeInsert()
          } else if (sql.trim().lowercase().startsWith("update") || sql.trim().lowercase().startsWith("delete")) {
            stmt.executeUpdateDelete().toLong()
          } else {
            // Otherwise it has to be a table-create, etc..
            stmt.execute()
            0
          }
        }
      }
    }

  open suspend fun <T> runActionReturningScoped(act: ActionReturning<T>): Flow<T> =
    flowWithConnection {
      if (!act.sql.trim().lowercase().startsWith("insert"))
        throw IllegalArgumentException("In SQLite a ActionReturning can only be an INSERT statement.")

      val conn = localConnection()
      val queryParams = AndroidxArrayWrapper(act.params.size)

      when (act) {
        is ActionReturningRow -> {
          if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            throw IllegalArgumentException(
              """Cannot use RETURNING clause in SQLite versions below 3.35.0. This requires Android SDK 34+ (Upside Down Cake)
                |Use Sql(...).actionReturningId() instead which will return a single Long id for the inserted row.
              """.trimMargin())

          // No caching used here, get the session directly
          tryCatchQuery(act.sql) {
            val sqliteQuery = SimpleSQLiteQuery(act.sql, queryParams.array)
            conn.value.session.query(sqliteQuery).use {
              val cursorWrapper = AndroidxCursorWrapper(it, windowSizeBytes)
              emitResultSet(cursorWrapper) { cursor -> act.resultMaker.makeExtractor(QueryDebugInfo(act.sql)).invoke(Unused, cursor) }
            }
          }
        }
        is ActionReturningId -> {
          accessStmt(act.sql, conn) { stmt ->
            prepare(wrap(stmt), Unused, act.params)
            tryCatchQuery(act.sql) {
              val id = stmt.executeInsert()
              emit(id as T)
            }
          }
        }
      }
    }

  override suspend fun <T> stream(query: Query<T>): Flow<T> =
    flowWithConnectionReadOnly {
      val conn = localConnection()
      val queryParams = AndroidxArrayWrapper(query.params.size)
      // No caching used here, get the session directly
      tryCatchQuery(query.sql) {
        val sqliteQuery = SimpleSQLiteQuery(query.sql, queryParams.array)
        conn.value.session.query(sqliteQuery).use {
          val cursorWrapper = AndroidxCursorWrapper(it, windowSizeBytes)
          emitResultSet(cursorWrapper) { cursor -> query.resultMaker.makeExtractor(QueryDebugInfo(query.sql)).invoke(Unused, cursor) }
        }
      }
    }

  open override suspend fun <T> run(query: Query<T>): List<T> = stream(query).toList()
  open override suspend fun run(query: Action): Long = runActionScoped(query.sql, query.params)
  open override suspend fun <T> run(query: ActionReturning<T>): T = runActionReturningScoped(query).first()
  open override suspend fun <T> stream(query: ActionReturning<T>): Flow<T> = runActionReturningScoped(query)

  override suspend fun run(query: BatchAction): List<Long> =
    throw IllegalArgumentException("Batch Actions are not supported in NativeContext.")

  override suspend fun <T> run(query: BatchActionReturning<T>): List<T> =
    throw IllegalArgumentException("Batch Queries are not supported in NativeContext.")

  override suspend fun <T> stream(query: BatchActionReturning<T>): Flow<T> =
    throw IllegalArgumentException("Batch Queries are not supported in NativeContext.")

  internal fun runRaw(sql: String) = runBlocking {
    sql.split(";").forEach { if (it.trim().isNotEmpty()) runActionScoped(it, emptyList()) }
  }

  override fun close() =
    this.pool.finalize()
}

/**
 * Used with SQLite to allow for non-writing operations (i.e. selects) to not use
 * the single-connection writer pool. This only makes sense of SQLite (particularily for WAL-mode)
 * where there can be a single writer concurrent to multiple readers which allows for
 * concurrency between the multiple writers and single reader. Note that in JDBC
 * this does not seem to be necessary because it is enfoced on the driver level
 * so we can keep the connection-per-thread (i.e. per coroutine) paradigm there.
 */
interface WithReadOnlyVerbs: RequiresSession<Connection, SupportSQLiteStatement> {
  val pool: AndroidPool

  suspend fun newReadOnlySession(): Connection = pool.borrowReader()

  // Check if there is at least a reader on th context, if it has a writer that's fine too
  fun CoroutineContext.hasOpenReadOrWriteConnection(): Boolean {
    val session = get(sessionKey)?.session
    return session != null && !isClosedSession(session)
  }

  suspend fun <T> withReadOnlyConnection(block: suspend CoroutineScope.() -> T): T {
    return if (coroutineContext.hasOpenReadOrWriteConnection()) {
      withContext(coroutineContext + Dispatchers.IO) { block() }
    } else {
      val session = newReadOnlySession()
      try {
        withContext(CoroutineSession(session, sessionKey) + Dispatchers.IO) { block() }
      } finally { closeSession(session) }
    }
  }

  // Use this with select queries in the case of Sqlite because they only require read connections.
  // The other flowWithConnection summons a writer connection so it is only necessary for actions that return.
  @OptIn(ExperimentalTypeInference::class)
  suspend fun <T> flowWithConnectionReadOnly(@BuilderInference block: suspend FlowCollector<T>.() -> Unit): Flow<T> {
    val flowInvoke = flow(block)
    // If there is any connection (including a writer) use it, otherwise create a reader
    // if we have a writer-connection already in the context use that for reading.
    return if (coroutineContext.hasOpenReadOrWriteConnection()) {
      flowInvoke.flowOn(CoroutineSession(localConnection(), sessionKey) + Dispatchers.IO)
    } else {
      val session = newReadOnlySession()
      flowInvoke.flowOn(CoroutineSession(session, sessionKey) + Dispatchers.IO).onCompletion { _ ->
        closeSession(session)
      }
    }
  }
}
