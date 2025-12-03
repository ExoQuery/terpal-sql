package io.exoquery.controller.jdbc

import io.exoquery.controller.*
import kotlinx.coroutines.flow.*
import javax.sql.DataSource
import java.sql.*

typealias RawColumnSet = List<Pair<String, String?>>

/**
 * This is a Terpal Driver, NOT a JDBC driver! It is the base class for all JDBC-based implementations of the
 * Terpal Driver base class `io.exoquery.sql.Driver`. This naming follows the conventions of SQL Delight
 * which is android/mobile centric and uses the term "Driver" to refer to refer to anything
 * involving the database connectivity susbsystem while the term Context is reserved for
 * semantics involving UI session control. As a JVM developer I am willing to use this naming convention
 * but only by the skin of my teeth.
 */
abstract class JdbcController internal constructor(
  override open val database: DataSource,
): ControllerCanonical<Connection, PreparedStatement, ResultSet, JdbcExecutionOptions>,
  WithEncoding<Connection, PreparedStatement, ResultSet>,
  HasTransactionalityJdbc
{
  override fun DefaultOpts(): JdbcExecutionOptions = JdbcExecutionOptions.Default()

  // use a lazy val here so they don't need to be recalculated every time
  override val allEncoders: Set<SqlEncoder<Connection, PreparedStatement, out Any>> by lazy { encodingApi.computeEncoders() + encodingConfig.additionalEncoders }
  override val allDecoders: Set<SqlDecoder<Connection, ResultSet, out Any>> by lazy { encodingApi.computeDecoders() + encodingConfig.additionalDecoders }
  // default starting index for contexts is Zero, JDBC PrepareStatements and row-numbers in ResultSet are 1-indexed
  override val startingStatementIndex = StartingIndex.One
  override val startingResultRowIndex = StartingIndex.One

  @TerpalSqlInternal
  override fun extractColumnInfo(row: ResultSet): List<ColumnInfo> =
    (1..row.metaData.columnCount).map { idx ->
      ColumnInfo(row.metaData.getColumnName(idx), row.metaData.getColumnTypeName(idx) ?: "<TYPE-UNKNOWN>")
    }

  @TerpalSqlInternal
  suspend fun <T> FlowCollector<Pair<T, RawColumnSet>>.emitResultSetBoth(conn: Connection, rs: ResultSet, extract: (Connection, ResultSet) -> T) {
    val meta = rs.metaData
    while (rs.next()) {
      val extracted = extract(conn, rs)
      val rowByRow = rs.makeColumnSet(meta)
      emit(extracted to rowByRow)
    }
  }

  @TerpalSqlInternal
  suspend fun <T> FlowCollector<T>.emitResultSet(conn: Connection, rs: ResultSet, extract: (Connection, ResultSet) -> T) {
    while (rs.next()) {
      //val meta = rs.metaData
      //println("--- Emit: ${(1..meta.columnCount).map { rs.getObject(it) }.joinToString(",")}")
      emit(extract(conn, rs))
    }
  }

  @TerpalSqlInternal
  protected open suspend fun <T> runActionReturningScoped(act: ControllerActionReturning<T>, options: JdbcExecutionOptions): Flow<T> =
    flowWithConnection(options) {
      val conn = localConnection()
      accessStmtReturning(act.sql, conn, options, act.returningColumns) { stmt ->
        prepare(stmt, conn, act.params)
        stmt.executeUpdate()
        emitResultSet(conn, options.prepareResult(stmt.generatedKeys), act.resultMaker.makeExtractor(QueryDebugInfo(act.sql)))
      }
    }

  @TerpalSqlInternal
  protected open suspend fun <T> runActionReturningBothScoped(act: ControllerActionReturning<T>, options: JdbcExecutionOptions): Flow<Pair<T, RawColumnSet>> =
    flowWithConnection(options) {
      val conn = localConnection()
      accessStmtReturning(act.sql, conn, options, act.returningColumns) { stmt ->
        prepare(stmt, conn, act.params)
        stmt.executeUpdate()
        emitResultSetBoth(conn, options.prepareResult(stmt.generatedKeys), act.resultMaker.makeExtractor(QueryDebugInfo(act.sql)))
      }
    }

  @TerpalSqlInternal
  protected open suspend fun runBatchActionScoped(query: ControllerBatchAction, options: JdbcExecutionOptions): List<Long> =
    withConnection(options) {
      val conn = localConnection()
      accessStmt(query.sql, conn) { stmt ->
        // Each set of params is a batch
        query.params.forEach { batch ->
          prepare(stmt, conn, batch)
          stmt.addBatch()
        }
        stmt.executeBatch().map { it.toLong() }.toList()
      }
    }

  @TerpalSqlInternal
  protected open suspend fun <T> runBatchActionReturningBothScoped(act: ControllerBatchActionReturning<T>, options: JdbcExecutionOptions): Flow<Pair<T, RawColumnSet>> =
    flowWithConnection(options) {
      val conn = localConnection()
      accessStmtReturning(act.sql, conn, options, act.returningColumns) { stmt ->
        // Each set of params is a batch
        act.params.forEach { batch ->
          prepare(stmt, conn, batch)
          stmt.addBatch()
        }
        stmt.executeBatch()
        emitResultSetBoth(conn, options.prepareResult(stmt.generatedKeys), act.resultMaker.makeExtractor(QueryDebugInfo(act.sql)))
      }
    }

  @TerpalSqlInternal
  protected open suspend fun <T> runBatchActionReturningScoped(act: ControllerBatchActionReturning<T>, options: JdbcExecutionOptions): Flow<T> =
    flowWithConnection(options) {
      val conn = localConnection()
      accessStmtReturning(act.sql, conn, options, act.returningColumns) { stmt ->
        // Each set of params is a batch
        act.params.forEach { batch ->
          prepare(stmt, conn, batch)
          stmt.addBatch()
        }
        stmt.executeBatch()
        emitResultSet(conn, options.prepareResult(stmt.generatedKeys), act.resultMaker.makeExtractor(QueryDebugInfo(act.sql)))
      }
    }

  @TerpalSqlInternal
  protected open suspend fun runActionScoped(sql: String, params: List<StatementParam<*>>, options: JdbcExecutionOptions): Long =
    withConnection(options) {
      val conn = localConnection()
       accessStmt(sql, conn) { stmt ->
        prepare(stmt, conn, params)
        tryCatchQuery(sql) {
          stmt.executeUpdate().toLong()
        }
      }
    }

  @TerpalSqlInternal
  override suspend fun <T> runRaw(query: ControllerQuery<T>, options: JdbcExecutionOptions): List<List<Pair<String, String?>>> =
    withConnection(options) {
      val conn = localConnection()
      accessStmt(query.sql, conn) { stmt ->
        prepare(stmt, conn, query.params)
        val result = mutableListOf<List<Pair<String, String?>>>()
        tryCatchQuery(query.sql) {
          options.prepareResult(stmt.executeQuery()).use { rs ->
            val meta = rs.metaData
            while (rs.next()) {
              result.add(rs.makeColumnSet(meta))
            }
          }
        }
        result
      }
    }

  private fun ResultSet.makeColumnSet(meta: ResultSetMetaData): RawColumnSet {
    val columns = mutableListOf<Pair<String, String?>>()
    for (i in 1..meta.columnCount) {
      columns.add(meta.getColumnName(i) to this.getString(i))
    }
    return columns
  }

  override open suspend fun <T> stream(query: ControllerQuery<T>, options: JdbcExecutionOptions): Flow<T> =
    flowWithConnection(options) {
      val conn = localConnection()
      accessStmt(query.sql, conn) { stmt ->
        prepare(stmt, conn, query.params)
        tryCatchQuery(query.sql) {
          options.prepareResult(stmt.executeQuery()).use { rs ->
            emitResultSet(conn, rs, query.resultMaker.makeExtractor(QueryDebugInfo(query.sql)))
          }
        }
      }
    }

  @TerpalSqlInternal
  suspend fun <T> streamBoth(query: ControllerQuery<T>, options: JdbcExecutionOptions): Flow<Pair<T, RawColumnSet>> =
    flowWithConnection(options) {
      val conn = localConnection()
      accessStmt(query.sql, conn) { stmt ->
        prepare(stmt, conn, query.params)
        tryCatchQuery(query.sql) {
          options.prepareResult(stmt.executeQuery()).use { rs ->
            emitResultSetBoth(conn, rs, query.resultMaker.makeExtractor(QueryDebugInfo(query.sql)))
          }
        }
      }
    }

  private inline fun <T> tryCatchQuery(sql: String, op: () -> T): T =
    try {
      op()
    } catch (e: SQLException) {
      throw SQLException("Error executing query: ${sql}", e)
    }

  @TerpalSqlInternal open suspend fun <T> runBoth(query: ControllerQuery<T>, options: JdbcExecutionOptions): List<Pair<T, RawColumnSet>> = streamBoth(query, options).toList()
  @TerpalSqlInternal open suspend fun <T> streamBoth(query: ControllerBatchActionReturning<T>, options: JdbcExecutionOptions): Flow<Pair<T, RawColumnSet>> = runBatchActionReturningBothScoped(query, options)
  @TerpalSqlInternal open suspend fun <T> streamBoth(query: ControllerActionReturning<T>, options: JdbcExecutionOptions): Flow<Pair<T, RawColumnSet>> = runActionReturningBothScoped(query, options)

  override open suspend fun <T> stream(query: ControllerBatchActionReturning<T>, options: JdbcExecutionOptions): Flow<T> = runBatchActionReturningScoped(query, options)
  override open suspend fun <T> stream(query: ControllerActionReturning<T>, options: JdbcExecutionOptions): Flow<T> = runActionReturningScoped(query, options)
  override open suspend fun <T> run(query: ControllerQuery<T>, options: JdbcExecutionOptions): List<T> = stream(query, options).toList()
  override open suspend fun run(query: ControllerAction, options: JdbcExecutionOptions): Long = runActionScoped(query.sql, query.params, options)
  override open suspend fun run(query: ControllerBatchAction, options: JdbcExecutionOptions): List<Long> = runBatchActionScoped(query, options)
  override open suspend fun <T> run(query: ControllerActionReturning<T>, options: JdbcExecutionOptions): T = stream(query, options).first()
  override open suspend fun <T> run(query: ControllerBatchActionReturning<T>, options: JdbcExecutionOptions): List<T> = stream(query, options).toList()
}
