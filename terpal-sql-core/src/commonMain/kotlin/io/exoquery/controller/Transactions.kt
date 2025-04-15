package io.exoquery.controller

import io.exoquery.controller.ControllerTransactional
import kotlinx.coroutines.CoroutineScope

suspend fun <T, Session, Stmt, ExecutionOpts> ControllerTransactional<Session, Stmt, ExecutionOpts>.transaction(executionOptions: ExecutionOpts, block: suspend ExternalTransactionScope<ExecutionOpts>.() -> T): T =
  withTransactionScope(executionOptions) {
    val coroutineScope = this
    block(ExternalTransactionScope<ExecutionOpts>(coroutineScope, this@transaction))
  }

suspend fun <T, Session, Stmt, ExecutionOpts> ControllerTransactional<Session, Stmt, ExecutionOpts>.transaction(block: suspend ExternalTransactionScope<ExecutionOpts>.() -> T): T =
  transaction(this.DefaultOpts(), block)

class ExternalTransactionScope<ExecutionOpts>(private val scope: CoroutineScope, private val ctx: Controller<ExecutionOpts>) {
  suspend fun <T> Query<T>.run(options: ExecutionOpts = ctx.DefaultOpts()): List<T> = ctx.run(this, options)
  suspend fun Action.run(options: ExecutionOpts = ctx.DefaultOpts()): Long = ctx.run(this, options)
  suspend fun BatchAction.run(options: ExecutionOpts = ctx.DefaultOpts()): List<Long> = ctx.run(this, options)
  suspend fun <T> ActionReturning<T>.run(options: ExecutionOpts = ctx.DefaultOpts()): T = ctx.run(this, options)
  suspend fun <T> BatchActionReturning<T>.run(options: ExecutionOpts = ctx.DefaultOpts()): List<T> = ctx.run(this, options)
}
