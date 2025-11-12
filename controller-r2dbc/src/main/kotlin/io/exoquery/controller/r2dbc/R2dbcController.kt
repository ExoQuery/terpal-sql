package io.exoquery.controller.r2dbc

import io.exoquery.controller.*
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Row
import io.r2dbc.spi.Statement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull

abstract class R2dbcController(
  override val encodingConfig: R2dbcEncodingConfig = R2dbcEncodingConfig.Default(),
  override val connectionFactory: ConnectionFactory
):
  ControllerCanonical<Connection, Statement, Row, R2dbcExecutionOptions>,
  WithEncoding<Connection, Statement, Row>,
  ControllerVerbs<R2dbcExecutionOptions>,
  HasTransactionalityR2dbc
{
  override fun DefaultOpts(): R2dbcExecutionOptions = R2dbcExecutionOptions.Default()
  override fun dbTypeIsRelevant(): Boolean = false

  override val encodingApi: R2dbcSqlEncoding =
    object: JavaSqlEncoding<Connection, Statement, Row>,
      BasicEncoding<Connection, Statement, Row> by R2dbcBasicEncoding,
      JavaTimeEncoding<Connection, Statement, Row> by R2dbcTimeEncoding,
      JavaUuidEncoding<Connection, Statement, Row> by R2dbcUuidEncodingNative {}

  override val allEncoders: Set<SqlEncoder<Connection, Statement, out Any>> by lazy { encodingApi.computeEncoders() + encodingConfig.additionalEncoders }
  override val allDecoders: Set<SqlDecoder<Connection, Row, out Any>> by lazy { encodingApi.computeDecoders() + encodingConfig.additionalDecoders }

  protected open fun changePlaceholders(sql: String): String = sql

  override fun extractColumnInfo(row: Row): List<ColumnInfo>? {
    val meta = row.metadata
    val cols = meta.columnMetadatas
    return cols.map { cmd ->
      ColumnInfo(cmd.name, cmd.type.name)
    }
  }

  override suspend fun <T> stream(act: ControllerQuery<T>, options: R2dbcExecutionOptions): Flow<T> =
    flowWithConnection(options) {
      val conn = localConnection()
      val preparedSql = changePlaceholders(act.sql)
      accessStmt(preparedSql, conn) { stmt ->
        prepare(stmt, conn, act.params)
        val pub = stmt.execute()
        val outputFlow = pub.awaitFirstOrNull()?.map { row, meta ->
          val resultMaker = act.resultMaker.makeExtractor(QueryDebugInfo(act.sql))
          PubResult(resultMaker(conn, row))
        }?.asFlow()?.map { it.value } ?: emptyFlow<T>()
        emitAll(outputFlow)
      }
    }

  override suspend fun <T> stream(act: ControllerBatchActionReturning<T>, options: R2dbcExecutionOptions): Flow<T> =
    flowWithConnection(options) {
      val conn = localConnection()
      val preparedSql = changePlaceholders(act.sql)
      // Create and execute a query for each param set and emit results from all queries into the flow
      act.params.forEach { params ->
        accessStmtReturning(preparedSql, conn, options, act.returningColumns) { stmt ->
          prepare(stmt, conn, params)
          val pub = stmt.execute().awaitFirstOrNull()
          val outputFlow = pub?.map { row, _ ->
            val resultMaker = act.resultMaker.makeExtractor(QueryDebugInfo(act.sql))
            PubResult(resultMaker(conn, row))
          }?.asFlow()?.map { it.value } ?: emptyFlow<T>()
          // Need to actually emit the flow into the surrounding flow that holds the connection
          emitAll(outputFlow)
        }
      }
    }

  override suspend fun <T> stream(act: ControllerActionReturning<T>, options: R2dbcExecutionOptions): Flow<T> =
    flowWithConnection(options) {
      val conn = localConnection()
      val preparedSql = changePlaceholders(act.sql)
      accessStmtReturning(preparedSql, conn, options, act.returningColumns) { stmt ->
        prepare(stmt, conn, act.params)
        val pub = stmt.execute().awaitFirstOrNull()
        val outputFlow = pub?.map { row, _ ->
          val resultMaker = act.resultMaker.makeExtractor(QueryDebugInfo(act.sql))
          PubResult(resultMaker(conn, row))
        }?.asFlow()?.map { it.value } ?: emptyFlow<T>()
        // Need to actually emit the flow into the surrounding flow that holds the connection
        emitAll(outputFlow)
      }
    }

  /** Need a temporary wrapper to work around limitation of pub-result being not-nullable */
  @JvmInline
  private value class PubResult<T>(val value: T)

  override suspend fun <T> run(query: ControllerQuery<T>, options: R2dbcExecutionOptions): List<T> =
    stream(query, options).toList()

  override suspend fun run(act: ControllerAction, options: R2dbcExecutionOptions): Long =
    flowWithConnection(options) {
      val conn = localConnection()
      val preparedSql = changePlaceholders(act.sql)
      accessStmt(preparedSql, conn) { stmt ->
        prepare(stmt, conn, act.params)
        // Execute and sum rowsUpdated across possibly multiple results
        val pub = stmt.execute()
        val numRows = pub.awaitFirstOrNull()?.rowsUpdated?.awaitFirstOrNull() ?: 0L
        emit(numRows)
      }
    }.first()

  override suspend fun run(query: ControllerBatchAction, options: R2dbcExecutionOptions): List<Long> =
    flowWithConnection(options) {
      val conn = localConnection()
      // TODO this statement works very well with caching, should look into reusing statements across calls
      val preparedSql = changePlaceholders(query.sql)
      accessStmtReturning(preparedSql, conn, options, emptyList()) { stmt ->
        val iter = query.params.iterator()
        while (iter.hasNext()) {
          val batch = iter.next()
          prepare(stmt, conn, batch)
          // We need to put a `add` after every batch except for the last one
          if (iter.hasNext()) {
            stmt.add()
          }
        }
        val pub = stmt.execute()
        // Here using the asFlow and connect actually makes sense because multiple results are expected
        pub.asFlow().collect { result ->
          val updated = result.rowsUpdated.awaitFirstOrNull() ?: 0
          emit(updated)
        }
      }
    }.toList()


  override suspend fun <T> run(query: ControllerActionReturning<T>, options: R2dbcExecutionOptions): T =
    stream(query, options).toList().first()

  override suspend fun <T> run(query: ControllerBatchActionReturning<T>, options: R2dbcExecutionOptions): List<T> =
    stream(query, options).toList()

  override suspend fun <T> runRaw(query: ControllerQuery<T>, options: R2dbcExecutionOptions) =
    flowWithConnection(options) {
      val conn = localConnection()
      val preparedSql = changePlaceholders(query.sql)
      accessStmt(preparedSql, conn) { stmt ->
        prepare(stmt, conn, query.params)
        val pub = stmt.execute()
        val outputFlow =
          pub.awaitFirstOrNull()?.map { row, meta ->
            val cols = meta.columnMetadatas
            cols.mapIndexed { i, md ->
              val name = md.name
              val value = row.get(i, Any::class.java)
              name to value?.toString()
            }
          }?.asFlow() ?: emptyFlow<List<Pair<String, String?>>>()
        emitAll(outputFlow)
      }
    }.toList()
}
