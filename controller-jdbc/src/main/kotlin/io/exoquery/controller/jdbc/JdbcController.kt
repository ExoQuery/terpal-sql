package io.exoquery.controller.jdbc

import io.exoquery.controller.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import javax.sql.DataSource
import kotlinx.datetime.TimeZone
import java.sql.*


/**
 * Most constructions will want to specify default values from AdditionalJdbcEncoding for additionalEncoders/decoders,
 * and they should have a simple construction JdbcEncodingConfig(...). Use `Empty` to make a config that does not
 * include these defaults. For this reason the real constructor is private.
 */
data class JdbcEncodingConfig private constructor(
  override val additionalEncoders: Set<SqlEncoder<Connection, PreparedStatement, out Any>>,
  override val additionalDecoders: Set<SqlDecoder<Connection, ResultSet, out Any>>,
  override val json: Json,
  // If you want to use any primitive-wrapped contextual encoders you need to add them here
  override val module: SerializersModule,
  override val timezone: TimeZone, override val debugMode: Boolean
): EncodingConfig<Connection, PreparedStatement, ResultSet> {
  companion object {
    val Default get() =
      Default(
        AdditionalJdbcEncoding.encoders,
        AdditionalJdbcEncoding.decoders
      )

    fun Default(
      additionalEncoders: Set<SqlEncoder<Connection, PreparedStatement, out Any>> = setOf(),
      additionalDecoders: Set<SqlDecoder<Connection, ResultSet, out Any>> = setOf(),
      json: Json = Json,
      module: SerializersModule = EmptySerializersModule(),
      timezone: TimeZone = TimeZone.currentSystemDefault(),
      debugMode: Boolean = false
    ) = JdbcEncodingConfig(
      additionalEncoders + AdditionalJdbcEncoding.encoders,
      additionalDecoders + AdditionalJdbcEncoding.decoders,
      json,
      module,
      timezone,
      debugMode
    )

    operator fun invoke(
      additionalEncoders: Set<SqlEncoder<Connection, PreparedStatement, out Any>> = setOf(),
      additionalDecoders: Set<SqlDecoder<Connection, ResultSet, out Any>> = setOf(),
      json: Json = Json,
      module: SerializersModule = EmptySerializersModule(),
      timezone: TimeZone = TimeZone.currentSystemDefault()
    ) = Default(additionalEncoders, additionalDecoders, json, module, timezone)

    fun Empty(
      additionalEncoders: Set<SqlEncoder<Connection, PreparedStatement, out Any>> = setOf(),
      additionalDecoders: Set<SqlDecoder<Connection, ResultSet, out Any>> = setOf(),
      json: Json = Json,
      module: SerializersModule = EmptySerializersModule(),
      timezone: TimeZone = TimeZone.currentSystemDefault()
    ) = JdbcEncodingConfig(additionalEncoders, additionalDecoders, json, module, timezone)
  }
}

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

  override fun extractColumnInfo(row: ResultSet): List<ColumnInfo> =
    (1..row.metaData.columnCount).map { idx ->
      ColumnInfo(row.metaData.getColumnName(idx), row.metaData.getColumnTypeName(idx) ?: "<TYPE-UNKNOWN>")
    }

  suspend fun <T> FlowCollector<T>.emitResultSet(conn: Connection, rs: ResultSet, extract: (Connection, ResultSet) -> T) {
    while (rs.next()) {
      //val meta = rs.metaData
      //println("--- Emit: ${(1..meta.columnCount).map { rs.getObject(it) }.joinToString(",")}")
      emit(extract(conn, rs))
    }
  }

  protected open suspend fun <T> runActionReturningScoped(act: ControllerActionReturning<T>, options: JdbcExecutionOptions): Flow<T> =
    flowWithConnection(options) {
      val conn = localConnection()
      accessStmtReturning(act.sql, conn, options, act.returningColumns) { stmt ->
        prepare(stmt, conn, act.params)
        stmt.executeUpdate()
        emitResultSet(conn, options.prepareResult(stmt.generatedKeys), act.resultMaker.makeExtractor(QueryDebugInfo(act.sql)))
      }
    }

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

  override suspend fun <T> runRaw(query: ControllerQuery<T>, options: JdbcExecutionOptions) =
    withConnection(options) {
      val conn = localConnection()
      accessStmt(query.sql, conn) { stmt ->
        prepare(stmt, conn, query.params)
        val result = mutableListOf<List<Pair<String, String?>>>()
        tryCatchQuery(query.sql) {
          options.prepareResult(stmt.executeQuery()).use { rs ->
            val meta = rs.metaData
            while (rs.next()) {
              for (i in 1..meta.columnCount) {
                val row = mutableListOf<Pair<String, String?>>()
                row.add(meta.getColumnName(i) to rs.getString(i))
                result.add(row)
              }
            }
          }
        }
        result
      }
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

  private inline fun <T> tryCatchQuery(sql: String, op: () -> T): T =
    try {
      op()
    } catch (e: SQLException) {
      throw SQLException("Error executing query: ${sql}", e)
    }

  override open suspend fun <T> stream(query: ControllerBatchActionReturning<T>, options: JdbcExecutionOptions): Flow<T> = runBatchActionReturningScoped(query, options)
  override open suspend fun <T> stream(query: ControllerActionReturning<T>, options: JdbcExecutionOptions): Flow<T> = runActionReturningScoped(query, options)
  override open suspend fun <T> run(query: ControllerQuery<T>, options: JdbcExecutionOptions): List<T> = stream(query, options).toList()
  override open suspend fun run(query: ControllerAction, options: JdbcExecutionOptions): Long = runActionScoped(query.sql, query.params, options)
  override open suspend fun run(query: ControllerBatchAction, options: JdbcExecutionOptions): List<Long> = runBatchActionScoped(query, options)
  override open suspend fun <T> run(query: ControllerActionReturning<T>, options: JdbcExecutionOptions): T = stream(query, options).first()
  override open suspend fun <T> run(query: ControllerBatchActionReturning<T>, options: JdbcExecutionOptions): List<T> = stream(query, options).toList()
}
