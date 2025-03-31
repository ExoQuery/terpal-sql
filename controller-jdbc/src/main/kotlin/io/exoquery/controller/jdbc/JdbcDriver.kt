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
  override val timezone: TimeZone
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
      timezone: TimeZone = TimeZone.currentSystemDefault()
    ) = JdbcEncodingConfig(
      additionalEncoders + AdditionalJdbcEncoding.encoders,
      additionalDecoders + AdditionalJdbcEncoding.decoders,
      json,
      module,
      timezone
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

@Deprecated("Use JdbcDriver instead", ReplaceWith("JdbcDriver"))
typealias JdbcContext = JdbcDriver

/**
 * This is a Terpal Driver, NOT a JDBC driver! It is the base class for all JDBC-based implementations of the
 * Terpal Driver base class `io.exoquery.sql.Driver`. This naming follows the conventions of SQL Delight
 * which is android/mobile centric and uses the term "Driver" to refer to refer to anything
 * involving the database connectivity susbsystem while the term Context is reserved for
 * semantics involving UI session control. As a JVM developer I am willing to use this naming convention
 * but only by the skin of my teeth.
 */
abstract class JdbcDriver internal constructor(
  override open val database: DataSource,
): DriverCannonical<Connection, PreparedStatement, ResultSet>,
  WithEncoding<Connection, PreparedStatement, ResultSet>,
  HasTransactionalityJdbc
{
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

  protected open suspend fun <T> runActionReturningScoped(act: ActionReturning<T>): Flow<T> =
    flowWithConnection {
      val conn = localConnection()
      accessStmtReturning(act.sql, conn, act.returningColumns) { stmt ->
        prepare(stmt, conn, act.params)
        stmt.executeUpdate()
        emitResultSet(conn, stmt.generatedKeys, act.resultMaker.makeExtractor(QueryDebugInfo(act.sql)))
      }
    }

  protected open suspend fun runBatchActionScoped(query: BatchAction): List<Long> =
    withConnection {
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

  protected open suspend fun <T> runBatchActionReturningScoped(act: BatchActionReturning<T>): Flow<T> =
    flowWithConnection {
      val conn = localConnection()
      accessStmtReturning(act.sql, conn, act.returningColumns) { stmt ->
        // Each set of params is a batch
        act.params.forEach { batch ->
          prepare(stmt, conn, batch)
          stmt.addBatch()
        }
        stmt.executeBatch()
        emitResultSet(conn, stmt.generatedKeys, act.resultMaker.makeExtractor(QueryDebugInfo(act.sql)))
      }
    }

  protected open suspend fun runActionScoped(sql: String, params: List<StatementParam<*>>): Long =
    withConnection {
      val conn = localConnection()
       accessStmt(sql, conn) { stmt ->
        prepare(stmt, conn, params)
        tryCatchQuery(sql) {
          stmt.executeUpdate().toLong()
        }
      }
    }

  override suspend fun <T> runRaw(query: Query<T>) =
    withConnection {
      val conn = localConnection()
      accessStmt(query.sql, conn) { stmt ->
        prepare(stmt, conn, query.params)
        val result = mutableListOf<Pair<String, String?>>()
        tryCatchQuery(query.sql) {
          stmt.executeQuery().use { rs ->
            rs.next()
            val meta = rs.metaData
            for (i in 1..meta.columnCount) {
              result.add(meta.getColumnName(i) to rs.getString(i))
            }
          }
        }
        result
      }
    }

  override open suspend fun <T> stream(query: Query<T>): Flow<T> =
    flowWithConnection {
      val conn = localConnection()
      accessStmt(query.sql, conn) { stmt ->
        prepare(stmt, conn, query.params)
        tryCatchQuery(query.sql) {
          stmt.executeQuery().use { rs ->
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

  override open suspend fun <T> stream(query: BatchActionReturning<T>): Flow<T> = runBatchActionReturningScoped(query)
  override open suspend fun <T> stream(query: ActionReturning<T>): Flow<T> = runActionReturningScoped(query)
  override open suspend fun <T> run(query: Query<T>): List<T> = stream(query).toList()
  override open suspend fun run(query: Action): Long = runActionScoped(query.sql, query.params)
  override open suspend fun run(query: BatchAction): List<Long> = runBatchActionScoped(query)
  override open suspend fun <T> run(query: ActionReturning<T>): T = stream(query).first()
  override open suspend fun <T> run(query: BatchActionReturning<T>): List<T> = stream(query).toList()
}
