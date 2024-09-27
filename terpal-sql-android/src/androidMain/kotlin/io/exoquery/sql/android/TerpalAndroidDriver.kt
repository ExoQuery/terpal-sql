package io.exoquery.sql.android

import android.content.Context
import android.database.sqlite.SQLiteException
import android.os.Build
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteStatement
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import io.exoquery.sql.*
import io.exoquery.sql.sqlite.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.experimental.ExperimentalTypeInference

sealed interface WalMode {
  object Enabled: WalMode
  /** A.k.a. The WAL "Compatibility" mode */
  object Default: WalMode
  object Disabled: WalMode
}

class TerpalAndroidDriver internal constructor(
  override val encodingConfig: AndroidEncodingConfig,
  override val pool: AndroidPool,
  override val walMode: WalMode,
  private val windowSizeBytes: Long? = null
): DriverTransactional<Connection, SupportSQLiteStatement>, java.io.Closeable,
  WithEncoding<Unused, SqliteStatementWrapper, SqliteCursorWrapper>,
  WithReadOnlyVerbs,
  HasTransactionalityAndroid {

  override fun prepareSession(session: Connection): Connection =
    session
//    session.apply {
//      when (walMode) {
//        is WalMode.Enabled -> this.value.session.enableWriteAheadLogging()
//        is WalMode.Default -> Unit
//        is WalMode.Disabled -> this.value.session.disableWriteAheadLogging()
//      }
//    }

  override val startingStatementIndex = StartingIndex.One
  override val startingResultRowIndex = StartingIndex.Zero

  sealed interface PoolingMode {
    /** Legacy Pre-WAL mode allowing only one reader and one writer */
    object SingleSessionLegacy: PoolingMode
    /** This is essentially Android's WAL-compatibility mode */
    object SingleSessionWal: PoolingMode
    /** Full-WAL mode allowing multiple readers and one single writer */
    data class MultipleReaderWal(val numReaders: Int): PoolingMode
  }
  companion object {
    val DEFAULT_CACHE_CAPACITY = 5

    internal fun makeOpenHelper(
      factory: SupportSQLiteOpenHelper.Factory,
      context: android.content.Context,
      databaseName: String,
      callback: SupportSQLiteOpenHelper.Callback,
      useNoBackupDirectory: Boolean,
      poolingMode: PoolingMode
    ) = run {
      val openHelper = factory.create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
          .name(databaseName)
          .noBackupDirectory(useNoBackupDirectory)
          .callback(callback)
          .build(),
      )
      when (poolingMode) {
        is PoolingMode.SingleSessionLegacy -> openHelper.setWriteAheadLoggingEnabled(false)
        is PoolingMode.SingleSessionWal -> Unit
        is PoolingMode.MultipleReaderWal -> openHelper.setWriteAheadLoggingEnabled(true)
      }
      openHelper
    }

    internal fun determinePoolingSetting(poolingMode: PoolingMode, makeHelper: () -> SupportSQLiteOpenHelper, cacheCapacity: Int): Pair<AndroidPool, WalMode> =
      when (poolingMode) {
        is PoolingMode.SingleSessionLegacy -> AndroidPool.SingleConnection(makeHelper(), cacheCapacity) to WalMode.Disabled
        is PoolingMode.SingleSessionWal -> AndroidPool.SingleConnection(makeHelper(), cacheCapacity) to WalMode.Default
        is PoolingMode.MultipleReaderWal -> AndroidPool.MultiConnection(makeHelper, cacheCapacity, poolingMode.numReaders) to WalMode.Enabled
      }

    suspend fun fromApplicationContext(
      databaseName: String,
      context: Context,
      schema: TerpalSchema<*> = EmptyTerpalSchema,
      poolingMode: PoolingMode = PoolingMode.SingleSessionWal,
      cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
      encodingConfig: AndroidEncodingConfig = AndroidEncodingConfig.Empty(),
      useNoBackupDirectory: Boolean = false,
      windowSizeBytes: Long? = null
    ): TerpalAndroidDriver {
      val makeHelper = { makeOpenHelper(FrameworkSQLiteOpenHelperFactory(), context, databaseName, schema.asSyncCallback(), useNoBackupDirectory, poolingMode) }
      val (pool, walMode) = determinePoolingSetting(poolingMode, makeHelper, cacheCapacity)
      return TerpalAndroidDriver(encodingConfig, pool, walMode, windowSizeBytes)
    }

    fun fromApplicationContext(
      databaseName: String,
      context: android.content.Context,
      callback: SupportSQLiteOpenHelper.Callback,
      factory: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory(),
      poolingMode: PoolingMode = PoolingMode.SingleSessionWal,
      cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
      encodingConfig: AndroidEncodingConfig = AndroidEncodingConfig.Empty(),
      useNoBackupDirectory: Boolean = false,
      windowSizeBytes: Long? = null
      ): TerpalAndroidDriver {
      val makeHelper = { makeOpenHelper(factory, context, databaseName, callback, useNoBackupDirectory, poolingMode) }
      val (pool, walMode) = determinePoolingSetting(poolingMode, makeHelper, cacheCapacity)
      return TerpalAndroidDriver(encodingConfig, pool, walMode, windowSizeBytes)
    }

    fun fromSingleOpenHelper(
      openHelper: SupportSQLiteOpenHelper,
      cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
      encodingConfig: AndroidEncodingConfig = AndroidEncodingConfig.Empty(),
      windowSizeBytes: Long? = null
    ): TerpalAndroidDriver {
      val pool = AndroidPool.WrappedUnsafe(openHelper.writableDatabase, cacheCapacity)
      return TerpalAndroidDriver(encodingConfig, pool, WalMode.Default, windowSizeBytes)
    }

    fun fromSingleSession(
      connection: SupportSQLiteDatabase,
      synchronizedAccess: Boolean = true,
      cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
      encodingConfig: AndroidEncodingConfig = AndroidEncodingConfig.Empty(),
      windowSizeBytes: Long? = null
    ): TerpalAndroidDriver {
      val pool =
        if (synchronizedAccess)
          AndroidPool.Wrapped(connection, cacheCapacity)
        else
          AndroidPool.WrappedUnsafe(connection, cacheCapacity)

      return TerpalAndroidDriver(encodingConfig, pool, WalMode.Default, windowSizeBytes)
    }
  }

  // Is there an open writer?
  override fun CoroutineContext.hasOpenConnection(): Boolean {
    val session = get(sessionKey)?.session
    return session != null && session.isWriter && !isClosedSession(session)
  }

  override suspend fun <T> withConnection(block: suspend CoroutineScope.() -> T): T {
    return if (coroutineContext.hasOpenConnection()) {
      // If there is an existing connection, run it on the whatever context it was set to run on. For example,
      // when the context starts up it might use the main-thread to setup the database and only later
      // switch to Dispatchers.IO. If we aggressively switch to Dispatchers.IO here we might cause
      // a deadlock (i.e. since a writer connection is already opened on the main thread.)
      withContext(coroutineContext) { block() }
    } else {
      val session = newSession()
      try {
        withContext(CoroutineSession(session, sessionKey) + Dispatchers.IO) { block() }
      } finally {
        closeSession(session)
      }
    }
  }

  suspend fun <T> withConnectionLabel(label: String?, block: suspend CoroutineScope.() -> T): T {
    return if (coroutineContext.hasOpenConnection()) {
      // If there is an existing connection, run it on the whatever context it was set to run on. For example,
      // when the context starts up it might use the main-thread to setup the database and only later
      // switch to Dispatchers.IO. If we aggressively switch to Dispatchers.IO here we might cause
      // a deadlock (i.e. since a writer connection is already opened on the main thread.)
      withContext(coroutineContext) { block() }
    } else {
      val session = newSession()
      try {
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

  protected fun <T> Query<T>.toSqliteQuery(): SimpleSQLiteQuery {
    val queryParams = AndroidxArrayWrapper(this.params.size)
    return SimpleSQLiteQuery(this.sql, queryParams.array)
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

  suspend fun <T> streamRaw(query: Query<T>): Flow<T> =
    flowWithConnectionReadOnly {
      val conn = localConnection()
      val queryParams = AndroidxArrayWrapper(query.params.size)
      // No caching used here, get the session directly
      tryCatchQuery(query.sql) {
        conn.value.session.query(query.toSqliteQuery()).use {
          val cursorWrapper = AndroidxCursorWrapper(it, windowSizeBytes)
          emitResultSet(cursorWrapper) { cursor -> query.resultMaker.makeExtractor(QueryDebugInfo(query.sql)).invoke(Unused, cursor) }
        }
      }
    }

  override suspend fun <T> runRaw(query: Query<T>) =
    withConnection {
      val conn = localConnection()
      val result = mutableListOf<Pair<String, String?>>()
      tryCatchQuery(query.sql) {
        conn.value.session.query(query.toSqliteQuery()).use { rs ->
          val meta = rs.columnNames
          for (i in 0 until meta.size) {
            result.add(meta[i] to rs.getString(i))
          }
        }
      }
      result
    }

  override open suspend fun <T> run(query: Query<T>): List<T> = run {
    withReadOnlyConnection() {
      val conn = localConnection()
      val result = mutableListOf<T>()
      // No caching used here, get the session directly
      tryCatchQuery(query.sql) {
        conn.value.session.query(query.toSqliteQuery()).use { rs ->
          val cursorWrapper = AndroidxCursorWrapper(rs, windowSizeBytes)
          while (cursorWrapper.next()) {
            val rowValue = query.resultMaker.makeExtractor(QueryDebugInfo(query.sql)).invoke(Unused, cursorWrapper)
            result.add(rowValue)
          }
        }
      }
      result
    }
  }

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

  fun prepareSession(session: Connection): Connection

  suspend fun newReadOnlySession(label: String? = null): Connection =
    prepareSession(pool.borrowReader())

  // Check if there is at least a reader on th context, if it has a writer that's fine too
  fun CoroutineContext.hasOpenReadOrWriteConnection(): Boolean {
    val session = get(sessionKey)?.session
    return session != null && !isClosedSession(session)
  }

  suspend fun <T> withReadOnlyConnection(block: suspend CoroutineScope.() -> T): T {
    return if (coroutineContext.hasOpenReadOrWriteConnection()) {
      // If there is an existing connection, run it on the whatever context it was set to run on. For example,
      // when the context starts up it might use the main-thread to setup the database and only later
      // switch to Dispatchers.IO. If we aggressively switch to Dispatchers.IO here we might cause
      // a deadlock (i.e. since a writer connection is already opened on the main thread.)
      withContext(coroutineContext) { block() }
    } else {
      val session = newReadOnlySession()
      try {
        withContext(CoroutineSession(session, sessionKey) + Dispatchers.IO) { block() }
      } finally {
        closeSession(session)
      }
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
      // Flows need to have their own context
      flowInvoke.flowOn(CoroutineSession(localConnection(), sessionKey))
    } else {
      val session = newReadOnlySession()
      flowInvoke.flowOn(CoroutineSession(session, sessionKey) + Dispatchers.IO).onCompletion { _ ->
        closeSession(session)
      }
    }
  }

}
