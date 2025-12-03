package io.exoquery.controller

import kotlinx.coroutines.CoroutineScope

@OptIn(TerpalSqlInternal::class)
suspend fun <T, Session, Stmt, ExecutionOpts> ControllerTransactional<Session, Stmt, ExecutionOpts>.transaction(executionOptions: ExecutionOpts, block: suspend ExternalTransactionScope<ExecutionOpts>.() -> T): T =
  withTransactionScope(executionOptions) {
    val coroutineScope = this
    block(ExternalTransactionScope<ExecutionOpts>(coroutineScope, this@transaction))
  }

@OptIn(TerpalSqlInternal::class)
suspend fun <T, Session, Stmt, ExecutionOpts> ControllerTransactional<Session, Stmt, ExecutionOpts>.transaction(block: suspend ExternalTransactionScope<ExecutionOpts>.() -> T): T =
  transaction(this.DefaultOpts(), block)

@OptIn(TerpalSqlInternal::class)
class ExternalTransactionScope<ExecutionOpts>(private val scope: CoroutineScope, private val ctx: Controller<ExecutionOpts>) {
  suspend fun <T> ControllerQuery<T>.run(options: ExecutionOpts = ctx.DefaultOpts()): List<T> = ctx.run(this, options)
  suspend fun ControllerAction.run(options: ExecutionOpts = ctx.DefaultOpts()): Long = ctx.run(this, options)
  suspend fun ControllerBatchAction.run(options: ExecutionOpts = ctx.DefaultOpts()): List<Long> = ctx.run(this, options)
  suspend fun <T> ControllerActionReturning<T>.run(options: ExecutionOpts = ctx.DefaultOpts()): T = ctx.run(this, options)
  suspend fun <T> ControllerBatchActionReturning<T>.run(options: ExecutionOpts = ctx.DefaultOpts()): List<T> = ctx.run(this, options)
}
