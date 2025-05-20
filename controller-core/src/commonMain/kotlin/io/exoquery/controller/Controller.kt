package io.exoquery.controller

import io.exoquery.controller.jdbc.CoroutineTransaction
import io.exoquery.controller.Messages.catchRethrowColumnInfoExtractError
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.TimeZone
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.intellij.lang.annotations.Language
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.experimental.ExperimentalTypeInference

class CoroutineSession<Session>(val session: Session, val sessionKey: CoroutineContext.Key<CoroutineSession<Session>>) : AbstractCoroutineContextElement(sessionKey) {
  override fun toString() = "CoroutineSession($sessionKey)"
}

class TerpalException(msg: String, cause: Throwable?): Exception(msg, cause) {
  constructor(msg: String): this(msg, null)
}

@OptIn(TerpalSqlInternal::class)
interface EncodingConfig<Session, Stmt, ResultRow> {
  val additionalEncoders: Set<SqlEncoder<Session, Stmt, out Any>>
  val additionalDecoders: Set<SqlDecoder<Session, ResultRow, out Any>>
  val json: Json
  // If you want to use any primitive-wrapped contextual encoders you need to add them here
  val module: SerializersModule
  val timezone: TimeZone
}

@OptIn(TerpalSqlInternal::class)
interface WithEncoding<Session, Stmt, ResultRow> {
  val encodingConfig: EncodingConfig<Session, Stmt, ResultRow>
  val startingStatementIndex: StartingIndex get() = StartingIndex.Zero // default for JDBC is 1 so this needs to be overrideable
  val startingResultRowIndex: StartingIndex get() = StartingIndex.Zero // default for JDBC is 1 so this needs to be overrideable

  fun createEncodingContext(session: Session, stmt: Stmt) =
    EncodingContext(session, stmt, encodingConfig.timezone)
  fun createDecodingContext(session: Session, row: ResultRow, debugInfo: QueryDebugInfo?) =
    DecodingContext(session, row, encodingConfig.timezone, startingResultRowIndex, catchRethrowColumnInfoExtractError { extractColumnInfo(row) }, debugInfo)

  fun extractColumnInfo(row: ResultRow): List<ColumnInfo>?

  val encodingApi: SqlEncoding<Session, Stmt, ResultRow>
  val allEncoders: Set<SqlEncoder<Session, Stmt, out Any>> get() = encodingApi.computeEncoders() + encodingConfig.additionalEncoders
  val allDecoders: Set<SqlDecoder<Session, ResultRow, out Any>> get() = encodingApi.computeDecoders() + encodingConfig.additionalDecoders

  fun failSql(msg: String): Nothing = throw TerpalException(msg)
  fun failSql(msg: String, e: Throwable): Nothing = throw TerpalException(msg, e)

  // TODO Maybe WithConcreteEncoding in order to not foce these implementations
  // Do it this way so we can avoid value casting in the runScoped function
  @Suppress("UNCHECKED_CAST")
  fun <T> StatementParam<T>.write(index: Int, conn: Session, ps: Stmt): Unit {
    // TODO logging integration
    //println("----- Preparing parameter $index - $value - using $serializer")
    PreparedStatementElementEncoder(createEncodingContext(conn, ps), index + startingStatementIndex.value, encodingApi, allEncoders, encodingConfig.module, encodingConfig.json).encodeNullableSerializableValue(serializer, value)
  }
  fun prepare(stmt: Stmt, conn: Session, params: List<StatementParam<*>>) =
    params.withIndex().forEach { (idx, param) ->
      param.write(idx, conn, stmt)
    }

  fun <T> KSerializer<T>.makeExtractor(debugInfo: QueryDebugInfo?) =
    { conn: Session, rs: ResultRow ->
      val decoder = RowDecoder(createDecodingContext(conn, rs, debugInfo), encodingConfig.module, encodingApi, allDecoders, descriptor, encodingConfig.json, startingResultRowIndex)
      // If this is specifically a top-level class annotated with @SqlJsonValue it needs special decoding
      if (this.descriptor.isJsonClassAnnotated()) {
        decoder.decodeJsonAnnotated(descriptor, 0, this) ?:
        failSql("Error decoding json annotated class of the type: ${this.descriptor}")
      } else {
        deserialize(decoder)
      }
    }
}

@OptIn(TerpalSqlInternal::class)
interface RequiresSession<Session, Stmt, ExecutionOpts> {

  // Methods that implementors need to provide
  val sessionKey: CoroutineContext.Key<CoroutineSession<Session>>
  abstract suspend fun newSession(executionOptions: ExecutionOpts): Session
  abstract fun closeSession(session: Session): Unit
  abstract fun isClosedSession(session: Session): Boolean
  suspend fun <R> accessStmt(sql: String, conn: Session, block: suspend (Stmt) -> R): R
  suspend fun <R> accessStmtReturning(sql: String, conn: Session, options: ExecutionOpts, returningColumns: List<String>, block: suspend (Stmt) -> R): R

  fun CoroutineContext.hasOpenConnection(): Boolean {
    val session = get(sessionKey)?.session
    return session != null && !isClosedSession(session)
  }

  suspend fun <T> withConnection(executionOptions: ExecutionOpts, block: suspend CoroutineScope.() -> T): T {
    return if (coroutineContext.hasOpenConnection()) {
      withContext(coroutineContext + Dispatchers.IO) { block() }
    } else {
      val session = newSession(executionOptions)
      try {
        withContext(CoroutineSession(session, sessionKey) + Dispatchers.IO) { block() }
      } finally { closeSession(session) }
    }
  }


  suspend fun localConnection() =
    coroutineContext.get(sessionKey)?.session ?: error("No connection detected in withConnection scope. This should be impossible.")

  @OptIn(ExperimentalTypeInference::class)
  suspend fun <T> flowWithConnection(executionOptions: ExecutionOpts, @BuilderInference block: suspend FlowCollector<T>.() -> Unit): Flow<T> {
    val flowInvoke = flow(block)
    return if (coroutineContext.hasOpenConnection()) {
      flowInvoke.flowOn(CoroutineSession(localConnection(), sessionKey) + Dispatchers.IO)
    } else {
      val session = newSession(executionOptions)
      flowInvoke.flowOn(CoroutineSession(session, sessionKey) + Dispatchers.IO).onCompletion { _ ->
        // It is interesting to note that in some implemenations of JDBC (e.g. MySQL) a connection will be returned to the pool
        // when transaction commit() happens (given that the actual transaction that occured BEFORE it succeeds and the commit() IO command itself succeeds).
        // In these sams implementations (e.g. MySQL), if a rollback() happens, the connection will not be returned to the pool and needs to be manually closed.
        // This behavior happens only when AutoCommit is disabled (which makes sense given that when AutoCommit=true the commit()/rollback() commands are moot).
        // Therefore it is very important to close the session here.
        // An interesting question to explore is if we can use session.use{} here instead of closeSession(session) and how that interacts with the mechanics
        // of the flowOn operator. Currently we cannot do this because session does not implement Closeable.
        closeSession(session)
      }
    }
  }
}

interface RequiresTransactionality<Session, Stmt, ExecutionOpts>: RequiresSession<Session, Stmt, ExecutionOpts> {
  abstract suspend fun <T> runTransactionally(block: suspend CoroutineScope.() -> T): T

  suspend fun <T> withTransactionScope(executionOptions: ExecutionOpts, block: suspend CoroutineScope.() -> T): T {
    val existingTransaction = coroutineContext[CoroutineTransaction]

    return when {
      existingTransaction == null ->
        withConnection(executionOptions) { runTransactionally { block() } }

      // This must mean it's a transaction { stuff... transaction { ... } } so let the outer transaction do the committing
      existingTransaction.incomplete ->
        withContext(coroutineContext) { block() }

      else -> error("Attempted to start new transaction within: $existingTransaction")
    }
  }
}

@OptIn(TerpalSqlInternal::class)
interface ControllerVerbs<ExecutionOpts> {
  fun DefaultOpts(): ExecutionOpts

  suspend fun <T> stream(query: ControllerQuery<T>, options: ExecutionOpts): Flow<T>
  suspend fun <T> stream(query: ControllerBatchActionReturning<T>, options: ExecutionOpts): Flow<T>
  suspend fun <T> stream(query: ControllerActionReturning<T>, options: ExecutionOpts): Flow<T>
  suspend fun <T> run(query: ControllerQuery<T>, options: ExecutionOpts): List<T>
  suspend fun run(query: ControllerAction, options: ExecutionOpts): Long
  suspend fun run(query: ControllerBatchAction, options: ExecutionOpts): List<Long>
  suspend fun <T> run(query: ControllerActionReturning<T>, options: ExecutionOpts): T
  suspend fun <T> run(query: ControllerBatchActionReturning<T>, options: ExecutionOpts): List<T>

  suspend fun <T> runRaw(query: ControllerQuery<T>, options: ExecutionOpts): List<List<Pair<String, String?>>>

  suspend fun <T> stream(query: ControllerQuery<T>): Flow<T> = stream(query, DefaultOpts())
  suspend fun <T> stream(query: ControllerBatchActionReturning<T>): Flow<T> = stream(query, DefaultOpts())
  suspend fun <T> stream(query: ControllerActionReturning<T>): Flow<T> = stream(query, DefaultOpts())
  suspend fun <T> run(query: ControllerQuery<T>): List<T> = run(query, DefaultOpts())
  suspend fun run(query: ControllerAction): Long = run(query, DefaultOpts())
  suspend fun run(query: ControllerBatchAction): List<Long> = run(query, DefaultOpts())
  suspend fun <T> run(query: ControllerActionReturning<T>): T = run(query, DefaultOpts())
  suspend fun <T> run(query: ControllerBatchActionReturning<T>): List<T> = run(query, DefaultOpts())

  suspend fun <T> runRaw(query: ControllerQuery<T>): List<List<Pair<String, String?>>> = runRaw(query, DefaultOpts())
}

/**
 * This is the base class of all Terpal Drivers (not to be confused with JDBC drivers or similar).
 * This is a minimal set of semantics needed to support Sql-interpolated query executions. This makes minimal assumptions
 * about how the context functionality is composed. Typically implementations will want to start with ContextBase or ContextCannonical.
 */
@OptIn(TerpalSqlInternal::class)
interface Controller<ExecutionOpts>: ControllerVerbs<ExecutionOpts> {

}

@OptIn(TerpalSqlInternal::class)
suspend fun Controller<*>.runActions(@Language("SQL") actions: String): List<Long> =
  actions.split(";").map { it.trim() }.filter { it.isNotEmpty() }.map { run(ControllerAction(it, listOf()), DefaultOpts()) }

@OptIn(TerpalSqlInternal::class)
interface ControllerTransactional<Session, Stmt, ExecutionOpts>: Controller<ExecutionOpts>, RequiresSession<Session, Stmt, ExecutionOpts>, RequiresTransactionality<Session, Stmt, ExecutionOpts> {
  fun showStats(): String = ""
}

/**
 * This is the interface that most implementors will use. It uses a Terpal's session-handling and transactionality mechanisms
 * and encoders. The assumption here is that the same Session/Row/Result types used in the encoders are used in the session-handling.
 * If this is not the case use ContextBase.
 */
@OptIn(TerpalSqlInternal::class)
interface ControllerCanonical<Session, Stmt, ResultRow, ExecutionOpts>: ControllerTransactional<Session, Stmt, ExecutionOpts>, RequiresSession<Session, Stmt, ExecutionOpts>, RequiresTransactionality<Session, Stmt, ExecutionOpts>, WithEncoding<Session, Stmt, ResultRow>

suspend fun <T> ControllerQuery<T>.runOn(ctx: Controller<*>) = ctx.run(this)
suspend fun <T> ControllerQuery<T>.streamOn(ctx: Controller<*>) = ctx.stream(this)
suspend fun <T> ControllerQuery<T>.runRawOn(ctx: Controller<*>) = ctx.runRaw(this)
suspend fun ControllerAction.runOn(ctx: Controller<*>) = ctx.run(this)
suspend fun <T> ControllerActionReturning<T>.runOn(ctx: Controller<*>) = ctx.run(this)
suspend fun ControllerBatchAction.runOn(ctx: Controller<*>) = ctx.run(this)
suspend fun <T> ControllerBatchActionReturning<T>.runOn(ctx: Controller<*>) = ctx.run(this)
suspend fun <T> ControllerBatchActionReturning<T>.streamOn(ctx: Controller<*>) = ctx.stream(this)
