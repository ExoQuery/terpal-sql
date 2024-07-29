package io.exoquery.sql.jdbc

import com.github.jasync.sql.db.ConcreteConnection
import com.github.jasync.sql.db.Connection as JConnection
import com.github.jasync.sql.db.RowData
import com.github.jasync.sql.db.pool.ConnectionPool
import com.github.jasync.sql.db.util.map
import io.exoquery.sql.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.asDeferred
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import java.sql.*
import java.time.OffsetDateTime
import java.util.*
import javax.sql.DataSource
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

typealias PreparedStmt = MutableList<Any?>

typealias JasyncEncodingContext = EncodingContext<JConnection, PreparedStmt>
typealias JasyncDecodingContext = DecodingContext<JConnection, RowData>

typealias JasyncEncoder<T> = SqlEncoder<JConnection, PreparedStmt, T>


/** Represents a Jasync Decoder with a nullable or non-nullable output value */
typealias JasyncDecoder<T> = SqlDecoder<JConnection, RowData, T>

/** Represents a Jasync Decoder with a non-nullable output value */
abstract class JasyncDecoderAny<T: Any>: JasyncDecoder<T>() {
  inline fun <reified R: Any> map(crossinline f: (T) -> R): JasyncDecoderAny<R> =
    object: JasyncDecoderAny<R>() {
      override val type = R::class
      override fun decode(ctx: JasyncDecodingContext, index: Int) =
        f(this@JasyncDecoderAny.decode(ctx, index))
    }

  override fun asNullable(): SqlDecoder<JConnection, RowData, T?> =
    object: SqlDecoder<JConnection, RowData, T?>() {
      override fun asNullable(): SqlDecoder<JConnection, RowData, T?> = this
      override val type = this@JasyncDecoderAny.type
      override fun decode(ctx: JasyncDecodingContext, index: Int): T? {
        return if (ctx.row.get(index) == null)
          null
        else
          this@JasyncDecoderAny.decode(ctx, index)
      }
    }

  companion object {
    inline fun <reified T: Any> fromFunction(crossinline f: (JasyncDecodingContext, Int) -> T?): JasyncDecoderAny<T> =
      object: JasyncDecoderAny<T>() {
        override val type = T::class
        override fun decode(ctx: JasyncDecodingContext, index: Int) =
          try {
            f(ctx, index) ?: throw EncodingException("Non-nullable Decoder returned null")
          } catch (e: Throwable) {
            // ${ctx.row.metaData.getColumnName(index)} TODO add columnNames:List<String> to DecodingContext so it can be used here
            throw EncodingException("Error decoding index: $index, column: (SEE TODO) and expected type: ${T::class}", e)
          }

      }
  }
}




/** Represents a Jdbc Encoder with a non-nullable input value */
abstract class JasyncEncoderAny<T: Any>: JasyncEncoder<T>() {

  override fun asNullable(): JasyncEncoder<T?> =
    object: JasyncEncoder<T?>() {
      override val type = this@JasyncEncoderAny.type
      override fun asNullable(): SqlEncoder<JConnection, PreparedStmt, T?> = this

      override fun encode(ctx: JasyncEncodingContext, value: T?, index: Int) =
        try {
          if (value != null)
            this@JasyncEncoderAny.encode(ctx, value, index)
          else {
            ctx.stmt.add(null)
            Unit
          }
        } catch (e: Throwable) {
          throw EncodingException("Error encoding ${type} value: $value at index: $index", e)
        }
    }

  inline fun <reified R: Any> contramap(crossinline f: (R) -> T):JasyncEncoderAny<R> =
    object: JasyncEncoderAny<R>() {
      override val type = R::class
      // Get the JDBC type from the parent. This makes sense because most of the time contramapped encoders are from primivites
      // e.g. StringDecoder.contramap { ... } so we want the jdbc type from the parent.
      override fun encode(ctx: JasyncEncodingContext, value: R, index: Int) =
        this@JasyncEncoderAny.encode(ctx, f(value), index)
    }

  companion object {
    inline fun <reified T: Any> fromFunction(crossinline f: (JasyncEncodingContext, T) -> Unit): JasyncEncoderAny<T> =
      object: JasyncEncoderAny<T>() {
        override val type = T::class
        override fun encode(ctx: JasyncEncodingContext, value: T, index: Int) {
          ctx.stmt.add(f(ctx, value))
        }
      }

    inline fun <reified T: Any> simple(): JasyncEncoderAny<T> =
      object: JasyncEncoderAny<T>() {
        override val type = T::class
        override fun encode(ctx: JasyncEncodingContext, value: T, index: Int) {
          ctx.stmt.add(value)
        }
      }
  }
}


open class JasyncEncodingBasic: BasicEncoding<JConnection, PreparedStmt, RowData> {

  override val ByteEncoder: JasyncEncoderAny<Byte> = JasyncEncoderAny.simple()
  override val CharEncoder: JasyncEncoderAny<Char> = JasyncEncoderAny.simple()
  override val DoubleEncoder: JasyncEncoderAny<Double> = JasyncEncoderAny.simple()
  override val FloatEncoder: JasyncEncoderAny<Float> = JasyncEncoderAny.simple()
  //...

  override val DateEncoder: JasyncEncoderAny<java.util.Date> = JasyncEncoderAny.fromFunction { ctx, date ->
    OffsetDateTime.ofInstant(date.toInstant(), ctx.timeZone.toZoneId()).toLocalDateTime()
  }
}


abstract class JasyncContext(override val database: ConnectionPool<*>): Context<JConnection, ConnectionPool<*>>() {
  protected open val module: SerializersModule = EmptySerializersModule()

  protected abstract val encodingApi: SqlEncoding<JConnection, PreparedStmt, RowData>

  protected open val timezone: TimeZone = TimeZone.getDefault()

  protected open val additionalEncoders: Set<SqlEncoder<JConnection, PreparedStmt, out Any>> = setOf()
  protected open val additionalDecoders: Set<SqlDecoder<JConnection, RowData, out Any>> = setOf()

  protected val allEncoders by lazy { encodingApi.computeEncoders() + additionalEncoders }
  protected val allDecoders by lazy { encodingApi.computeDecoders() + additionalDecoders }
  protected val json: Json = Json

  protected open fun createEncodingContext(session: JConnection, stmt: PreparedStmt) = EncodingContext(session, stmt, timezone)
  protected open fun createDecodingContext(session: JConnection, row: RowData) = DecodingContext(session, row, timezone)

  fun <T> Param<T>.write(index: Int, conn: JConnection, ps: PreparedStmt): Unit {
    // TODO logging integration
    //println("----- Preparing parameter $index - $value - using $serializer")
    PreparedStatementElementEncoder(createEncodingContext(conn, ps), index+1, encodingApi, allEncoders, module, json).encodeNullableSerializableValue(serializer, value)
  }

  protected fun <T> KSerializer<T>.makeExtractor() =
    { conn: JConnection, rs: RowData ->
      val decoder = JasyncRowDecoder(createDecodingContext(conn, rs), module, encodingApi, allDecoders, descriptor, json)
      // If this is specifically a top-level class annotated with @SqlJsonValue it needs special decoding
      if (this.descriptor.isJsonClassAnnotated()) {
        decoder.decodeJsonAnnotated(descriptor, 0, this) ?:
          throw SQLException("Error decoding json annotated class of the type: ${this.descriptor}")
      } else {
        deserialize(decoder)
      }
    }

  protected open fun prepare(stmt: PreparedStmt, conn: JConnection, params: List<Param<*>>) =
    params.withIndex().forEach { (idx, param) ->
      param.write(idx, conn, stmt)
    }

  open suspend fun <T> run(query: Query<T>): List<T> =
    database.inTransaction { conn ->
      val stmt: PreparedStmt = mutableListOf<Any?>()
      prepare(stmt, conn, query.params)
      conn.sendPreparedStatement(query.sql).map { result ->
        result.rows.map { rowData ->
          val extractor = query.resultMaker.makeExtractor()
          extractor(conn, rowData)
        }
      }
    }.asDeferred().await()

}

abstract class JdbcContext(override val database: DataSource): Context<Connection, DataSource>() {
  // Maybe should just have all the encdoers from the base SqlEncoders class an everything introduced after should be added via additionalEncoders.
  // that would make it much easier to reason about what encoders fome from where

  // Need to do this first in iniitalization
  protected open val additionalEncoders: Set<SqlEncoder<Connection, PreparedStatement, out Any>> = AdditionaJdbcTimeEncoding.encoders
  protected open val additionalDecoders: Set<SqlDecoder<Connection, ResultSet, out Any>> = AdditionaJdbcTimeEncoding.decoders
  protected open val timezone: TimeZone = TimeZone.getDefault()

  // If you want to use any primitive-wrapped contextual encoders you need to add them here
  protected open val module: SerializersModule = EmptySerializersModule()

  protected abstract val encodingApi: SqlEncoding<Connection, PreparedStatement, ResultSet>

  override open fun newSession(): Connection = database.connection
  override open fun closeSession(session: Connection): Unit = session.close()
  override open fun isClosedSession(session: Connection): Boolean = session.isClosed

  protected open fun createEncodingContext(session: Connection, stmt: PreparedStatement) = EncodingContext(session, stmt, timezone)
  protected open fun createDecodingContext(session: Connection, row: ResultSet) = DecodingContext(session, row, timezone)

  protected val JdbcCoroutineContext = object: CoroutineContext.Key<CoroutineSession<Connection>> {}
  override val sessionKey: CoroutineContext.Key<CoroutineSession<Connection>> = JdbcCoroutineContext

  override open suspend fun <T> runTransactionally(block: suspend CoroutineScope.() -> T): T {
    val session = coroutineContext.get(sessionKey)?.session ?: error("No connection found")
    session.runWithManualCommit {
      val transaction = CoroutineTransaction()
      try {
        val result = withContext(transaction) { block() }
        commit()
        return result
      } catch (ex: Throwable) {
        rollback()
        throw ex
      } finally {
        transaction.complete()
      }
    }
  }

  internal inline fun <T> Connection.runWithManualCommit(block: Connection.() -> T): T {
    val before = autoCommit

    return try {
      autoCommit = false
      this.run(block)
    } finally {
      autoCommit = before
    }
  }

  protected val allEncoders by lazy { encodingApi.computeEncoders() + additionalEncoders }
  protected val allDecoders by lazy { encodingApi.computeDecoders() + additionalDecoders }
  protected val json: Json = Json

  // Do it this way so we can avoid value casting in the runScoped function
  @Suppress("UNCHECKED_CAST")
  fun <T> Param<T>.write(index: Int, conn: Connection, ps: PreparedStatement): Unit {
    // TODO logging integration
    //println("----- Preparing parameter $index - $value - using $serializer")
    PreparedStatementElementEncoder(createEncodingContext(conn, ps), index+1, encodingApi, allEncoders, module, json).encodeNullableSerializableValue(serializer, value)
  }

  protected open fun makeStmtReturning(sql: String, conn: Connection, returningColumns: List<String>) =
    if (returningColumns.isNotEmpty())
      conn.prepareStatement(sql, returningColumns.toTypedArray())
    else
      conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)

  protected open fun makeStmt(sql: String, conn: Connection) =
    conn.prepareStatement(sql)

  protected open fun prepare(stmt: PreparedStatement, conn: Connection, params: List<Param<*>>) =
    params.withIndex().forEach { (idx, param) ->
      param.write(idx, conn, stmt)
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
      makeStmtReturning(act.sql, conn, act.returningColumns).use { stmt ->
        prepare(stmt, conn, act.params)
        stmt.executeUpdate()
        emitResultSet(conn, stmt.generatedKeys, act.resultMaker.makeExtractor())
      }
    }

  protected open suspend fun runBatchActionScoped(query: BatchAction): List<Int> =
    withConnection {
      val conn = localConnection()
      makeStmt(query.sql, conn).use { stmt ->
        // Each set of params is a batch
        query.params.forEach { batch ->
          prepare(stmt, conn, batch)
          stmt.addBatch()
        }
        stmt.executeBatch().toList()
      }
    }

  protected open suspend fun <T> runBatchActionReturningScoped(act: BatchActionReturning<T>): Flow<T> =
    flowWithConnection {
      val conn = localConnection()
      makeStmtReturning(act.sql, conn, act.returningColumns).use { stmt ->
        // Each set of params is a batch
        act.params.forEach { batch ->
          prepare(stmt, conn, batch)
          stmt.addBatch()
        }
        stmt.executeBatch()
        emitResultSet(conn, stmt.generatedKeys, act.resultMaker.makeExtractor())
      }
    }

  protected open suspend fun runActionScoped(sql: String, params: List<Param<*>>): Int =
    withConnection {
      val conn = localConnection()
       makeStmt(sql, conn).use { stmt ->
        prepare(stmt, conn, params)
        tryCatchQuery(sql) {
          stmt.executeUpdate()
        }
      }
    }

  protected fun <T> KSerializer<T>.makeExtractor() =
    { conn: Connection, rs: ResultSet ->
      val decoder = JdbcRowDecoder(createDecodingContext(conn, rs), module, encodingApi, allDecoders, descriptor, json)
      // If this is specifically a top-level class annotated with @SqlJsonValue it needs special decoding
      if (this.descriptor.isJsonClassAnnotated()) {
        decoder.decodeJsonAnnotated(descriptor, 0, this) ?:
          throw SQLException("Error decoding json annotated class of the type: ${this.descriptor}")
      } else {
        deserialize(decoder)
      }
    }

  internal open suspend fun <T> stream(query: Query<T>): Flow<T> =
    flowWithConnection {
      val conn = localConnection()
      makeStmt(query.sql, conn).use { stmt ->
        prepare(stmt, conn, query.params)
        tryCatchQuery(query.sql) {
          stmt.executeQuery().use { rs ->
            emitResultSet(conn, rs, query.resultMaker.makeExtractor())
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

  internal open suspend fun <T> stream(query: BatchActionReturning<T>): Flow<T> = runBatchActionReturningScoped(query)
  internal open suspend fun <T> stream(query: ActionReturning<T>): Flow<T> = runActionReturningScoped(query)
  internal open suspend fun <T> run(query: Query<T>): List<T> = stream(query).toList()
  internal open suspend fun run(query: Action): Int = runActionScoped(query.sql, query.params)
  internal open suspend fun run(query: BatchAction): List<Int> = runBatchActionScoped(query)
  internal open suspend fun <T> run(query: ActionReturning<T>): T = stream(query).first()
  internal open suspend fun <T> run(query: BatchActionReturning<T>): List<T> = stream(query).toList()

  suspend open fun <T> transaction(block: suspend ExternalTransactionScope.() -> T): T =
    withTransactionScope {
      val coroutineScope = this
      block(ExternalTransactionScope(coroutineScope, this@JdbcContext))
    }
}

suspend fun <T> Query<T>.runOn(ctx: JdbcContext) = ctx.run(this)
suspend fun <T> Query<T>.streamOn(ctx: JdbcContext) = ctx.stream(this)
suspend fun Action.runOn(ctx: JdbcContext) = ctx.run(this)
suspend fun <T> ActionReturning<T>.runOn(ctx: JdbcContext) = ctx.run(this)
suspend fun BatchAction.runOn(ctx: JdbcContext) = ctx.run(this)
suspend fun <T> BatchActionReturning<T>.runOn(ctx: JdbcContext) = ctx.run(this)
suspend fun <T> BatchActionReturning<T>.streamOn(ctx: JdbcContext) = ctx.stream(this)

data class ExternalTransactionScope(val scope: CoroutineScope, val ctx: JdbcContext) {
  suspend fun <T> Query<T>.run(): List<T> = ctx.run(this)
  suspend fun Action.run(): Int = ctx.run(this)
  suspend fun BatchAction.run(): List<Int> = ctx.run(this)
  suspend fun <T> ActionReturning<T>.run(): T = ctx.run(this)
  suspend fun <T> BatchActionReturning<T>.run(): List<T> = ctx.run(this)
}
