package io.exoquery.controller.r2dbc

import io.exoquery.controller.*
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.reactivestreams.Publisher

// Minimal execution options placeholder for R2DBC
data class R2dbcExecutionOptions(
  val debug: Boolean = false
) {
  companion object {
    fun Default() = R2dbcExecutionOptions()
  }
}

class R2dbcController(private val connectionFactory: ConnectionFactory): ControllerVerbs<R2dbcExecutionOptions> {
  override fun DefaultOpts(): R2dbcExecutionOptions = R2dbcExecutionOptions.Default()

  // Helper to create a connection and ensure closure
  private suspend fun <T> withConnection(block: suspend (Connection) -> T): T {
    val conn = connectionFactory.create().awaitSingle()
    try {
      return block(conn)
    } finally {
      conn.close().awaitFirstOrNull()
    }
  }

  private fun bindParams(stmt: io.r2dbc.spi.Statement, params: List<StatementParam<*>>) {
    var idx = 0
    for (p in params) {
      // Bind by index; most R2DBC drivers accept basic Kotlin/Java types directly
      @Suppress("UNCHECKED_CAST")
      val v: Any? = (p.value as Any?)
      stmt.bind(idx, v)
      idx += 1
    }
  }

  override suspend fun <T> stream(query: ControllerQuery<T>, options: R2dbcExecutionOptions): Flow<T> {
    // Decoding using resultMaker requires a full encoding implementation which is out of scope here.
    // Provided for API completeness.
    throw UnsupportedOperationException("R2dbcController.stream(query) decoding not yet implemented")
  }

  override suspend fun <T> stream(query: ControllerBatchActionReturning<T>, options: R2dbcExecutionOptions): Flow<T> {
    throw UnsupportedOperationException("R2dbcController.stream(batchReturning) not yet implemented")
  }

  override suspend fun <T> stream(query: ControllerActionReturning<T>, options: R2dbcExecutionOptions): Flow<T> {
    throw UnsupportedOperationException("R2dbcController.stream(actionReturning) not yet implemented")
  }

  override suspend fun <T> run(query: ControllerQuery<T>, options: R2dbcExecutionOptions): List<T> = stream(query, options).toList()

  override suspend fun run(query: ControllerAction, options: R2dbcExecutionOptions): Long =
    withConnection { conn ->
      val stmt = conn.createStatement(query.sql)
      bindParams(stmt, query.params)
      // Execute and sum rowsUpdated across possibly multiple results
      val pub = stmt.execute()
      var total = 0L
      pub.asFlow().collect { result ->
        val updated = result.rowsUpdated.awaitFirstOrNull() ?: 0
        total += updated.toLong()
      }
      total
    }

  override suspend fun run(query: ControllerBatchAction, options: R2dbcExecutionOptions): List<Long> =
    withConnection { conn ->
      // TODO this statement works very well with caching, should look into reusing statements across calls
      val stmt = conn.createStatement(query.sql)
      // Add batches
      query.params.forEach { batch ->
        bindParams(stmt, batch)
        stmt.add()
      }
      val results = mutableListOf<Long>()
      val pub = stmt.execute()
      pub.asFlow().collect { result ->
        val updated = result.rowsUpdated.awaitFirstOrNull() ?: 0
        results.add(updated.toLong())
      }
      results
    }

  override suspend fun <T> run(query: ControllerActionReturning<T>, options: R2dbcExecutionOptions): T =
    stream(query, options).toList().first()

  override suspend fun <T> run(query: ControllerBatchActionReturning<T>, options: R2dbcExecutionOptions): List<T> =
    stream(query, options).toList()

  override suspend fun <T> runRaw(query: ControllerQuery<T>, options: R2dbcExecutionOptions): List<List<Pair<String, String?>>> =
    withConnection { conn ->
      val stmt = conn.createStatement(query.sql)
      bindParams(stmt, query.params)
      val results = mutableListOf<List<Pair<String, String?>>>()
      val pub = stmt.execute()
      // Each Result may contain rows; map them to name->string pairs for all columns

      // convert the publisher into a suspeding function
      //pub.awaitFirstOrNull()?.map { row, meta ->
      //  val cols = meta.columnMetadatas
      //  val pairs = cols.mapIndexed { i, md ->
      //    val name = md.name
      //    val value = row.get(i, Any::class.java)
      //    name to value?.toString()
      //  }
      //  pairs
      //}?.asFlow() ?: emptyFlow()

      pub.asFlow()
        .flatMapConcat { r ->
          r.map { row, meta ->
            val cols = meta.columnMetadatas
            val pairs = cols.mapIndexed { i, md ->
              val name = md.name
              val value = row.get(i, Any::class.java)
              name to value?.toString()
            }
            pairs
          }.asFlow()
        }
        .collect { rowPairs ->
          results.add(rowPairs)
        }
      results
    }
}
