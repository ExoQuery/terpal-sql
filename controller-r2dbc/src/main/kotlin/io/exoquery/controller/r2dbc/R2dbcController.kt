package io.exoquery.controller.r2dbc

import io.exoquery.controller.*
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Row
import io.r2dbc.spi.Statement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.collect

class R2dbcController(
  override val encodingConfig: R2dbcEncodingConfig = R2dbcEncodingConfig.Default(),
  override val connectionFactory: ConnectionFactory
):
  ControllerCanonical<Connection, Statement, Row, R2dbcExecutionOptions>,
  WithEncoding<Connection, Statement, Row>,
  ControllerVerbs<R2dbcExecutionOptions>,
  HasTransactionalityR2dbc
{
  override fun DefaultOpts(): R2dbcExecutionOptions = R2dbcExecutionOptions.Default()

  override val encodingApi: R2dbcSqlEncoding =
    object: JavaSqlEncoding<Connection, Statement, Row>,
      BasicEncoding<Connection, Statement, Row> by R2dbcBasicEncoding,
      JavaTimeEncoding<Connection, Statement, Row> by R2dbcTimeEncoding,
      JavaUuidEncoding<Connection, Statement, Row> by R2dbcUuidEncoding {}


  // Helper to create a connection and ensure closure
  //private suspend fun <T> withConnection(block: suspend (Connection) -> T): T {
  //  val conn = connectionFactory.create().awaitSingle()
  //  try {
  //    return block(conn)
  //  } finally {
  //    conn.close().awaitFirstOrNull()
  //  }
  //}

  override fun extractColumnInfo(row: Row): List<ColumnInfo>? {
    val meta = row.metadata
    val cols = meta.columnMetadatas
    return cols.map { cmd ->
      ColumnInfo(cmd.name, cmd.type.name)
    }
  }

  override suspend fun <T> stream(act: ControllerQuery<T>, options: R2dbcExecutionOptions): Flow<T> =
    withConnection(options) {
      val conn = localConnection()
      accessStmt(act.sql, conn) { stmt ->
        prepare(stmt, conn, act.params)
        val pub = stmt.execute() // TODO try-catch here?
        pub.awaitFirstOrNull()?.map { row, meta ->
          val resultMaker = act.resultMaker.makeExtractor(QueryDebugInfo(act.sql))
          PubResult(resultMaker(conn, row))
        }?.asFlow()?.map { it.value } ?: emptyFlow()
      }
    }

  override suspend fun <T> stream(query: ControllerBatchActionReturning<T>, options: R2dbcExecutionOptions): Flow<T> {
    throw UnsupportedOperationException("R2dbcController.stream(batchReturning) not yet implemented")
  }

  override suspend fun <T> stream(act: ControllerActionReturning<T>, options: R2dbcExecutionOptions): Flow<T> =
    withConnection(options) {
      val conn = localConnection()
      accessStmt(act.sql, conn) { stmt ->
        prepare(stmt, conn, act.params)
        val results = mutableListOf<List<Pair<String, String?>>>()
        val pub = stmt.execute() // TODO try-catch here?
        // Each Result may contain rows; map them to name->string pairs for all columns

        // convert the publisher into a suspeding function
        pub.awaitFirstOrNull()?.map { row, meta ->
          val resultMaker = act.resultMaker.makeExtractor(QueryDebugInfo(act.sql))
          PubResult(resultMaker(conn, row))
        }?.asFlow()?.map { it.value } ?: emptyFlow()
      }
    }

  /** Need a temporary wrapper to work around limitation of pub-result being not-nullable */
  @JvmInline
  private value class PubResult<T>(val value: T)

  override suspend fun <T> run(query: ControllerQuery<T>, options: R2dbcExecutionOptions): List<T> = stream(query, options).toList()

  override suspend fun run(act: ControllerAction, options: R2dbcExecutionOptions): Long =
    withConnection(options) {
      val conn = localConnection()
      accessStmt(act.sql, conn) { stmt ->
        prepare(stmt, conn, act.params)
        // Execute and sum rowsUpdated across possibly multiple results
        val pub = stmt.execute()
        pub.awaitFirstOrNull()?.rowsUpdated?.awaitFirstOrNull() ?: 0
      }
    }

  override suspend fun run(query: ControllerBatchAction, options: R2dbcExecutionOptions): List<Long> =
    withConnection(options) {
      val conn = localConnection()
      // TODO this statement works very well with caching, should look into reusing statements across calls
      accessStmtReturning(query.sql, conn, options, emptyList()) { stmt ->
        query.params.forEach { batch ->
          prepare(stmt, conn, batch)
          stmt.add()
        }
        val results = mutableListOf<Long>()
        val pub = stmt.execute()
        // Here using the asFlow and connect actually makes sense because multiple results are expected
        pub.asFlow().collect { result ->
          val updated = result.rowsUpdated.awaitFirstOrNull() ?: 0
          results.add(updated.toLong())
        }
        results
      }
    }

  override suspend fun <T> run(query: ControllerActionReturning<T>, options: R2dbcExecutionOptions): T =
    stream(query, options).toList().first()

  override suspend fun <T> run(query: ControllerBatchActionReturning<T>, options: R2dbcExecutionOptions): List<T> =
    stream(query, options).toList()

  override suspend fun <T> runRaw(query: ControllerQuery<T>, options: R2dbcExecutionOptions) =
    withConnection(options) {
      val conn = localConnection()
      val stmt = conn.createStatement(query.sql)
      prepare(stmt, conn, query.params)
      val results = mutableListOf<List<Pair<String, String?>>>()
      val pub = stmt.execute()
      // Each Result may contain rows; map them to name->string pairs for all columns

      // convert the publisher into a suspeding function
      pub.awaitFirstOrNull()?.map { row, meta ->
        val cols = meta.columnMetadatas
        val pairs = cols.mapIndexed { i, md ->
          val name = md.name
          val value = row.get(i, Any::class.java)
          name to value?.toString()
        }
        pairs
      }?.collect { rowPairs -> results.add(rowPairs) }
      results
    }
}
