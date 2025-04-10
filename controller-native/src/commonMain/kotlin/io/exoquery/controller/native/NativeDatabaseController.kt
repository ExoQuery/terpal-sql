package io.exoquery.controller.native

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.*
import co.touchlab.sqliter.Statement
import co.touchlab.sqliter.interop.SQLiteException
import io.exoquery.controller.delight.CursorWrapper
import io.exoquery.controller.delight.DelightCursorWrapper
import io.exoquery.controller.delight.DelightStatementWrapper
import io.exoquery.controller.*
import io.exoquery.controller.sqlite.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.experimental.ExperimentalTypeInference

data class Versions(val oldVersion: Int, val newVersion: Int)

object UnusedOpts {}

@OptIn(ExperimentalStdlibApi::class)
class NativeDatabaseController internal constructor(
  override val encodingConfig: EncodingConfig<Unused, SqliteStatementWrapper, SqliteCursorWrapper>,
  override val pool: SqliterPool,
): ControllerTransactional<Connection, Statement, UnusedOpts>, AutoCloseable,
  WithEncoding<Unused, SqliteStatementWrapper, SqliteCursorWrapper>,
  WithReadOnlyVerbs,
  HasTransactionalityNative {

  override val startingStatementIndex = StartingIndex.One
  override val startingResultRowIndex = StartingIndex.Zero

  override fun DefaultOpts(): UnusedOpts = UnusedOpts

  sealed interface PoolingMode {
    object Single: PoolingMode
    data class Multiple(val numReaders: Int): PoolingMode
  }

  companion object {
    val DEFAULT_CACHE_CAPACITY = 5

    /**
     * Create a NativeContext from an sqliter DatabaseConfiguration.
     * Since during creation of the NativeContext we will potentially run SQL migration scripts
     * the creation of the context needs to be run in Dispatchers.IO.
     */
    fun fromDatabaseConfiguration(
      nativeConfig: DatabaseConfiguration,
      poolingMode: PoolingMode = PoolingMode.Single,
      cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
      encodingConfig: NativeEncodingConfig = NativeEncodingConfig()
    ): NativeDatabaseController {
      val db = createDatabaseManager(nativeConfig)
      val pool = SqliterPool(
        when (poolingMode) {
          is PoolingMode.Single -> SqliterPoolType.SingleConnection(db)
          is PoolingMode.Multiple -> SqliterPoolType.MultiConnection(db, poolingMode.numReaders)
        },
        cacheCapacity
      )
      return NativeDatabaseController(encodingConfig, pool)
    }

    fun fromSchema(
      schema: TerpalSchema<Unit>,
      dbName: String,
      basePath: String? = null,
      mode: PoolingMode = PoolingMode.Single,
      cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
      encodingConfig: NativeEncodingConfig = NativeEncodingConfig()
    ): NativeDatabaseController {
      val nativeConfig =
        DatabaseConfiguration(
          name = dbName,
          inMemory = false,
          version = schema.version.toInt(),
          create = { conn -> schema.toCreateCallbackSync(fromSingleConnection(conn)) },
          upgrade = { connection, oldVersion, newVersion ->
            schema.toMigrateCallbackSync(
              fromSingleConnection(connection),
              oldVersion.toLong(),
              newVersion.toLong()
            )
          },
          extendedConfig = DatabaseConfiguration.Extended(basePath = basePath)
        )
      return fromDatabaseConfiguration(nativeConfig, mode, cacheCapacity, encodingConfig)
    }


    /**
     * TODO move this into a extension function and make SqlDelight a provided (i.e. optional) dependency
     *      (i.e. if no reference to it in this class shuold not give class-missing errors if not present)
     * Create a NativeContext from a SqlDelight schema, database name and some other optional parameters.
     * Since during creation of the NativeContext we will potentially run SQL migration scripts
     * the creation of the context needs to be run in Dispatchers.IO.
     */
    fun fromSchema(
      schema: SqlSchema<QueryResult.Value<Unit>>,
      dbName: String,
      basePath: String? = null,
      mode: PoolingMode = PoolingMode.Single,
      cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
      encodingConfig: NativeEncodingConfig = NativeEncodingConfig()
    ): NativeDatabaseController {
      val nativeConfig =
        DatabaseConfiguration(
          name = dbName,
          inMemory = false,
          version = if (schema.version > Int.MAX_VALUE) error("Schema version is larger than Int.MAX_VALUE: ${schema.version}.") else schema.version.toInt(),
          create = { connection -> wrapConnection(connection) { schema.create(it) } },
          upgrade = { connection, oldVersion, newVersion ->
            wrapConnection(connection) { schema.migrate(it, oldVersion.toLong(), newVersion.toLong()) }
          },
          extendedConfig = DatabaseConfiguration.Extended(basePath = basePath)
        )
      return fromDatabaseConfiguration(nativeConfig, mode, cacheCapacity, encodingConfig)
    }

    fun create(
      dbFileName: String,
      dbfilePath: String? = null,
      version: Int = 1,
      mode: PoolingMode = PoolingMode.Single,
      cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
      encodingConfig: NativeEncodingConfig = NativeEncodingConfig(),
      createCallback: (NativeDatabaseController) -> Unit = {},
      updateCallback: (NativeDatabaseController, Versions) -> Unit = { _, _ -> }
    ): NativeDatabaseController {
      val nativeConfig =
        DatabaseConfiguration(
          name = dbFileName,
          inMemory = false,
          version = version,
          create = { createCallback(fromSingleConnection(it)) },
          upgrade = { db, old, new -> updateCallback(fromSingleConnection(db), Versions(old, new)) },
          extendedConfig = DatabaseConfiguration.Extended(basePath = dbfilePath)
        )
      return fromDatabaseConfiguration(nativeConfig, mode, cacheCapacity, encodingConfig)
    }

    /**
     * Create a NativeContext from a single connection. Doesn't need to be suspended because there is no effect happening.
     * The connection is already open.
     */
    fun fromSingleConnection(conn: DatabaseConnection, encodingConfig: NativeEncodingConfig = NativeEncodingConfig()): NativeDatabaseController =
      NativeDatabaseController(encodingConfig, SqliterPool(SqliterPoolType.Wrapped(conn), DEFAULT_CACHE_CAPACITY))
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

  override fun extractColumnInfo(row: SqliteCursorWrapper): List<ColumnInfo>? =
    when {
      // This context always uses the DelightCursorWrapper and always use sthe SqliterSqlCursor. If this is not the case it is probably an error.
      row is CursorWrapper -> {
        val realRow = row.cursor
        (0..<realRow.columnCount).map { idx ->
          ColumnInfo(realRow.columnName(idx), realRow.getType(idx).name)
        }
      }
      else -> null
    }

  override suspend fun <R> accessStmt(sql: String, conn: Connection, block: suspend (Statement) -> R): R {
    if (sql.trim().isEmpty()) throw IllegalStateException("SQL statement is empty")
    //println("------------- Creating: ${sql} ---------------")
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

  private inline fun <T> tryCatchQuery(sql: String, op: () -> T): T =
    try {
      op()
    } catch (e: SQLiteException) {
      failSql("Error executing query: ${sql}", e)
    }

  suspend fun <T> FlowCollector<T>.emitResultSet(rs: CursorWrapper, extract: (CursorWrapper) -> T) {
    while (rs.next()) {
      emit(extract(rs))
    }
  }

  override val encodingApi = SqliteSqlEncoding

  suspend fun runActionScoped(sql: String, params: List<StatementParam<*>>, options: UnusedOpts): Long =
    withConnection(options) {
      //println("------------ Running Block")
      val conn = localConnection()
      //println("------------ Got connection: $conn")
      accessStmt(sql, conn) { stmt ->
        //println("------------ Preparing query")
        prepare(DelightStatementWrapper(stmt), Unused, params)
        //println("------------ Trying query")
        tryCatchQuery(sql) {
          if (sql.trim().lowercase().startsWith("insert")) {
            //println("------------ Running Insert")
            stmt.executeInsert()
          } else if (sql.trim().lowercase().startsWith("update") || sql.trim().lowercase().startsWith("delete")) {
            //println("------------ Running Update")
            stmt.executeUpdateDelete().toLong()
          } else {

            stmt.execute()
            0
          }
        }
      }
    }

  open suspend fun <T> runActionReturningScoped(act: ActionReturning<T>, options: UnusedOpts): Flow<T> =
    flowWithConnection(options) {
      val conn = localConnection()
      when (act) {
        is ActionReturningId -> {
          accessStmtReturning(act.sql, conn, options, emptyList()) { stmt ->
            prepare(DelightStatementWrapper(stmt), Unused, act.params)
            tryCatchQuery(act.sql) {
              val id = stmt.executeInsert()
              emit(id as T) // this should only be callable when the id is a Long
            }
          }
        }
        is ActionReturningRow -> {
          accessStmtReturning(act.sql, conn, options, act.returningColumns) { stmt ->
            prepare(DelightStatementWrapper(stmt), Unused, act.params)
            tryCatchQuery(act.sql) {
              stmt.query().let { rs ->
                val wrappedCursor = DelightCursorWrapper(rs)
                emitResultSet(wrappedCursor, { act.resultMaker.makeExtractor(QueryDebugInfo(act.sql)).invoke(Unused, it) })
              }
            }
          }
        }
      }
    }

  override suspend fun <T> stream(query: Query<T>, options: UnusedOpts): Flow<T> =
    flowWithConnectionReadOnly(options) {
      val conn = localConnection()
      accessStmt(query.sql, conn) { stmt ->
        prepare(DelightStatementWrapper(stmt), Unused, query.params)
        tryCatchQuery(query.sql) {
          stmt.query().let { rs ->
            val wrappedCursor = DelightCursorWrapper(rs)
            emitResultSet(wrappedCursor, { query.resultMaker.makeExtractor(QueryDebugInfo(query.sql)).invoke(Unused, it) })
          }
        }
      }
    }

  override suspend fun <T> runRaw(query: Query<T>, options: UnusedOpts) =
    withReadOnlyConnection(options) {
      val conn = localConnection()
      accessStmt(query.sql, conn) { stmt ->
        prepare(DelightStatementWrapper(stmt), Unused, query.params)
        val result = mutableListOf<Pair<String, String?>>()
        tryCatchQuery(query.sql) {
          stmt.query().let { rs ->
            rs.next()
            for (i in 1..rs.columnCount) {
              result.add(rs.columnName(i) to rs.getString(i))
            }
          }
        }
        result
      }
    }

  open override suspend fun <T> run(query: Query<T>, options: UnusedOpts): List<T> = stream(query, options).toList()
  open override suspend fun run(query: Action, options: UnusedOpts): Long = runActionScoped(query.sql, query.params, options)
  open override suspend fun <T> run(query: ActionReturning<T>, options: UnusedOpts): T = runActionReturningScoped(query, options).first()
  open override suspend fun <T> stream(query: ActionReturning<T>, options: UnusedOpts): Flow<T> = runActionReturningScoped(query, options)

  override suspend fun run(query: BatchAction, options: UnusedOpts): List<Long> =
    throw IllegalArgumentException("Batch Actions are not supported in NativeContext.")

  override suspend fun <T> run(query: BatchActionReturning<T>, options: UnusedOpts): List<T> =
    throw IllegalArgumentException("Batch Queries are not supported in NativeContext.")

  override suspend fun <T> stream(query: BatchActionReturning<T>, options: UnusedOpts): Flow<T> =
    throw IllegalArgumentException("Batch Queries are not supported in NativeContext.")

  fun runRaw(sql: String, options: UnusedOpts = UnusedOpts) = runBlocking {
    sql.split(";").forEach { if (it.trim().isNotEmpty()) runActionScoped(it, emptyList(), options) }
  }

  override fun close() {
    pool.finalize()
  }
}

/**
 * Used with SQLite to allow for non-writing operations (i.e. selects) to not use
 * the single-connection writer pool. This only makes sense of SQLite (particularily for WAL-mode)
 * where there can be a single writer concurrent to multiple readers which allows for
 * concurrency between the multiple writers and single reader. Note that in JDBC
 * this does not seem to be necessary because it is enfoced on the driver level
 * so we can keep the connection-per-thread (i.e. per coroutine) paradigm there.
 */
interface WithReadOnlyVerbs: RequiresSession<Connection, Statement, UnusedOpts> {
  val pool: SqliterPool

  suspend fun newReadOnlySession(): Connection = pool.borrowReader()

  // Check if there is at least a reader on th context, if it has a writer that's fine too
  fun CoroutineContext.hasOpenReadOrWriteConnection(): Boolean {
    val session = get(sessionKey)?.session
    return session != null && !isClosedSession(session)
  }

  suspend fun <T> withReadOnlyConnection(options: UnusedOpts, block: suspend CoroutineScope.() -> T): T {
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
  suspend fun <T> flowWithConnectionReadOnly(options: UnusedOpts, @BuilderInference block: suspend FlowCollector<T>.() -> Unit): Flow<T> {
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
