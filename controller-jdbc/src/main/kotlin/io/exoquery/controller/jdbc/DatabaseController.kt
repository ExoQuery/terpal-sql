package io.exoquery.controller.jdbc

import io.exoquery.controller.JavaSqlEncoding
import io.exoquery.controller.JavaTimeEncoding
import io.exoquery.controller.JavaUuidEncoding
import io.exoquery.controller.*
import kotlinx.coroutines.flow.Flow
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import javax.sql.DataSource

/**
 * This is the primary constructor object for Terpal JDBC drivers. It is a collection of Terpal drivers for various databases.
 */
object DatabaseController {
  /**
   * Use this for most modern postgres versions.
   */
  open class Postgres(
    override val database: DataSource,
    encodingConfig: JdbcEncodingConfig = JdbcEncodingConfig.Default
  ): JdbcController(database) {
    override val encodingApi: JdbcSqlEncoding =
      object: JavaSqlEncoding<Connection, PreparedStatement, ResultSet>,
        BasicEncoding<Connection, PreparedStatement, ResultSet> by JdbcBasicEncoding,
        JavaTimeEncoding<Connection, PreparedStatement, ResultSet> by PostgresTimeEncoding,
        JavaUuidEncoding<Connection, PreparedStatement, ResultSet> by JdbcUuidObjectEncoding {}

    // Postgrees comes with its own default encoders, to exclude them need to override this property direct
    override val encodingConfig =
      encodingConfig.copy(
        additionalEncoders = encodingConfig.additionalEncoders + AdditionalPostgresEncoding.encoders,
        additionalDecoders = encodingConfig.additionalDecoders + AdditionalPostgresEncoding.decoders
      )

    // Postgres does not support Types.TIME_WITH_TIMEZONE as a JDBC type but does have a `TIME WITH TIMEZONE` datatype this is puzzling.
    object PostgresTimeEncoding: JdbcTimeEncoding() {
      override val jdbcTypeOfOffsetTime = Types.TIME
    }

    companion object { }
  }

  /**
   * Use this with Postgres previous to the `TIME WITH TIMEZONE` datatype (i.e. 9.2 where this datatype first became functional).
   */
  open class PostgresLegacy(
    override val database: DataSource,
    encodingConfig: JdbcEncodingConfig = JdbcEncodingConfig.Default
  ): JdbcController(database) {
    override val encodingApi: JdbcSqlEncoding =
      object: JavaSqlEncoding<Connection, PreparedStatement, ResultSet>,
        BasicEncoding<Connection, PreparedStatement, ResultSet> by JdbcBasicEncoding,
        JavaTimeEncoding<Connection, PreparedStatement, ResultSet> by JdbcTimeEncodingLegacy,
        JavaUuidEncoding<Connection, PreparedStatement, ResultSet> by JdbcUuidObjectEncoding {}

    override val encodingConfig =
      encodingConfig.copy(
        additionalEncoders = encodingConfig.additionalEncoders + AdditionalPostgresEncoding.encoders,
        additionalDecoders = encodingConfig.additionalDecoders + AdditionalPostgresEncoding.decoders
      )

    companion object { }
  }

  open class H2(
    override val database: DataSource,
    override val encodingConfig: JdbcEncodingConfig = JdbcEncodingConfig.Default
  ): JdbcController(database) {
    override val encodingApi: JdbcSqlEncoding =
      object: JavaSqlEncoding<Connection, PreparedStatement, ResultSet>,
        BasicEncoding<Connection, PreparedStatement, ResultSet> by JdbcBasicEncoding,
        JavaTimeEncoding<Connection, PreparedStatement, ResultSet> by JdbcTimeEncoding(),
        JavaUuidEncoding<Connection, PreparedStatement, ResultSet> by JdbcUuidObjectEncoding {}

    companion object { }
  }

  open class Mysql(
    override val database: DataSource,
    override val encodingConfig: JdbcEncodingConfig = JdbcEncodingConfig.Default
    ): JdbcController(database) {
    override val encodingApi: JdbcSqlEncoding =
      object : JavaSqlEncoding<Connection, PreparedStatement, ResultSet>,
        BasicEncoding<Connection, PreparedStatement, ResultSet> by JdbcBasicEncoding,
        JavaTimeEncoding<Connection, PreparedStatement, ResultSet> by MysqlTimeEncoding,
        JavaUuidEncoding<Connection, PreparedStatement, ResultSet> by JdbcUuidStringEncoding {}

    object MysqlTimeEncoding: JdbcTimeEncoding() {
      override val jdbcTypeOfZonedDateTime  = Types.TIMESTAMP
      override val jdbcTypeOfInstant        = Types.TIMESTAMP
      override val jdbcTypeOfOffsetTime     = Types.TIME
      override val jdbcTypeOfOffsetDateTime = Types.TIMESTAMP
    }
    companion object { }
  }

  open class Sqlite(
    override val database: DataSource,
    override val encodingConfig: JdbcEncodingConfig = JdbcEncodingConfig.Default
  ): JdbcController(database) {
    override val encodingApi: JdbcSqlEncoding =
      object : JavaSqlEncoding<Connection, PreparedStatement, ResultSet>,
        BasicEncoding<Connection, PreparedStatement, ResultSet> by JdbcBasicEncoding,
        JavaTimeEncoding<Connection, PreparedStatement, ResultSet> by JdbcTimeEncodingLegacy,
        JavaUuidEncoding<Connection, PreparedStatement, ResultSet> by JdbcUuidStringEncoding {}

    protected override open suspend fun <T> runActionReturningScoped(act: ActionReturning<T>): Flow<T> =
      flowWithConnection {
        val conn = localConnection()
        when (act) {
          is ActionReturningId -> {
            accessStmtReturning(act.sql, conn, act.returningColumns) { stmt ->
              prepare(stmt, conn, act.params)
              stmt.execute()
              // Mutliple columns could be returned and the user might want a specific combined type in which they are stored
              emitResultSet(
                conn,
                stmt.generatedKeys,
                { conn, rs -> act.resultMaker.makeExtractor<T>(QueryDebugInfo(act.sql)).invoke(conn, rs) })
            }
          }
          is ActionReturningRow -> {
            accessStmtReturning(act.sql, conn, act.returningColumns) { stmt ->
              prepare(stmt, conn, act.params)
              emitResultSet(conn, stmt.executeQuery(), act.resultMaker.makeExtractor<T>(QueryDebugInfo(act.sql)))
            }
          }
        }
      }

    protected override open suspend fun <T> runBatchActionReturningScoped(act: BatchActionReturning<T>): Flow<T> =
      flowWithConnection {
        val conn = localConnection()
        act.params.forEach { batch ->
          when (act) {
            is BatchActionReturningId ->
              accessStmtReturning(act.sql, conn, emptyList()) { stmt ->
                prepare(stmt, conn, batch)
                emitResultSet(
                  conn,
                  stmt.generatedKeys,
                  { conn, rs -> act.resultMaker.makeExtractor<T>(QueryDebugInfo(act.sql)).invoke(conn, rs) as T })
              }
            is BatchActionReturningRow ->
              accessStmtReturning(act.sql, conn, act.returningColumns) { stmt ->
                prepare(stmt, conn, batch)
                emitResultSet(conn, stmt.executeQuery(), act.resultMaker.makeExtractor<T>(QueryDebugInfo(act.sql)))
              }
          }
        }
      }

    companion object { }
  }

  open class Oracle(
    override val database: DataSource,
    override val encodingConfig: JdbcEncodingConfig = JdbcEncodingConfig.Default
  ): JdbcController(database) {
    override val encodingApi: JdbcSqlEncoding =
      object : JavaSqlEncoding<Connection, PreparedStatement, ResultSet>,
        BasicEncoding<Connection, PreparedStatement, ResultSet> by JdbcEncodingOracle,
        JavaTimeEncoding<Connection, PreparedStatement, ResultSet> by OracleTimeEncoding,
        JavaUuidEncoding<Connection, PreparedStatement, ResultSet> by JdbcUuidStringEncoding {}

    object OracleTimeEncoding: JdbcTimeEncoding() {
      // Normally it is Types.TIME by in that case Oracle truncates the milliseconds
      override val jdbcTypeOfLocalTime  = Types.TIMESTAMP
      override val jdbcTypeOfOffsetTime = Types.TIME
    }
    // Oracle has this crazy behavior where empty strings are treated as NULLs in JDBC. Need to account for that by converting to "" when
    // the getString method returns null. Need to account for this behavior by turning null values from getString into empty strings.
    // The getString function is used in the StringDecoder as well as the CharDecoder.
    // Note that this will not mess up the functionality of a Nullable decoder (i.e. the result of JdbcEncoder.asNullable() because the
    // nullable decoder first checks the row using wasNull() before calling the non-nullable decoder. If the row is null then the non-null
    // decoder is not invoked so we would not care about it converting a `null` value to an empty String either way.
    // This same logic applies to the ByteArrayDecoder as well.
    object JdbcEncodingOracle: JdbcBasicEncoding() {
      override val CharDecoder: JdbcDecoderAny<Char> = JdbcDecoderAny(Char::class) { ctx, i -> ctx.row.getString(i)?.let { it[0] } ?: Char.MIN_VALUE }
      override val StringDecoder: JdbcDecoderAny<String> = JdbcDecoderAny(String::class) { ctx, i -> (ctx.row.getString(i) ?: "") }
      override val ByteArrayDecoder: JdbcDecoderAny<ByteArray> = JdbcDecoderAny(ByteArray::class) { ctx, i -> ctx.row.getBytes(i) ?: byteArrayOf() }
      // More oracle crazy behavior that requires encoding booleans as ints
      override val BooleanEncoder: JdbcEncoderAny<Boolean> = JdbcEncoderAny(Types.INTEGER, Boolean::class) { ctx, v, i -> ctx.stmt.setInt(i, if (v) 1 else 0) }
      override val BooleanDecoder: JdbcDecoderAny<Boolean> = JdbcDecoderAny(Boolean::class) { ctx, i -> ctx.row.getInt(i) == 1 }
    }

    companion object { }
  }

  open class SqlServer(
    override val database: DataSource,
    override val encodingConfig: JdbcEncodingConfig = JdbcEncodingConfig.Default
  ): JdbcController(database) {
    override val encodingApi: JdbcSqlEncoding =
      object : JavaSqlEncoding<Connection, PreparedStatement, ResultSet>,
        BasicEncoding<Connection, PreparedStatement, ResultSet> by JdbcBasicEncoding,
        JavaTimeEncoding<Connection, PreparedStatement, ResultSet> by JdbcTimeEncoding(),
        JavaUuidEncoding<Connection, PreparedStatement, ResultSet> by JdbcUuidStringEncoding {}

    override suspend fun <T> runActionReturningScoped(act: ActionReturning<T>): Flow<T> =
      flowWithConnection {
        val conn = localConnection()
        when (act) {
          is ActionReturningId -> {
            // TODO error looks like it should be impossible!
            accessStmtReturning(act.sql, conn, act.returningColumns) { stmt ->
              prepare(stmt, conn, act.params)
              // This is another oddity in SQL Server where the statement needs to be executed before .getGeneratedKeys() can be called.
              // If it is not used, when stmt.getGeneratedKeys() is called it will throw the following exception:
              // com.microsoft.sqlserver.jdbc.SQLServerException: The statement must be executed before any results can be obtained.
              stmt.execute()
              emitResultSet(
                conn,
                stmt.generatedKeys,
                { conn, rs -> act.resultMaker.makeExtractor<T>(QueryDebugInfo(act.sql)).invoke(conn, rs) as T })
            }
          }
          is ActionReturningRow -> {
            accessStmtReturning(act.sql, conn, act.returningColumns) { stmt ->
              prepare(stmt, conn, act.params)
              // See comment about SQL Server not supporting getGeneratedKeys below
              emitResultSet(conn, stmt.executeQuery(), act.resultMaker.makeExtractor(QueryDebugInfo(act.sql)))
            }
          }
        }
      }

    override suspend fun <T> runBatchActionReturningScoped(act: BatchActionReturning<T>): Flow<T> =
      flowWithConnection {
        val conn = localConnection()
        accessStmtReturning(act.sql, conn, listOf()) { stmt ->
          act.params.forEach { batch ->
            prepare(stmt, conn, batch)
            // The SQL Server driver has no ability to either do getGeneratedKeys or executeQuery
            // at the end of a sequence of addBatch calls to get all inserted keys/executed queries
            // (whether a `OUTPUT` clause is used in the Query or not). That means that in order
            // be able to get any results, we need to use extractResult(ps.executeQuery, ...)
            // on every single inserted batch! See the following mssql-jdbc issues for more detail:
            // https://github.com/microsoft/mssql-jdbc/issues/358
            // https://github.com/Microsoft/mssql-jdbc/issues/245
            // Also note that some libraries like Slick specifically mention that returning-keys is generally
            // not supported when jdbc-batching is used:
            // https://github.com/slick/slick/blob/06ccee3cdc0722adeb8bb0658afb4a0d3524b119/slick/src/main/scala/slick/jdbc/JdbcActionComponent.scala#L654
            // Therefore slick falls back to single-row-insert batching when insertion with getGeneratedKeys is used
            stmt.addBatch()
            emitResultSet(conn, stmt.executeQuery(), act.resultMaker.makeExtractor(QueryDebugInfo(act.sql)))
          }
        }
      }

    companion object { }
  }
}