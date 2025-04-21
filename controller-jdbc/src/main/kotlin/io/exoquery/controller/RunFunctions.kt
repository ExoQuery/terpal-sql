package io.exoquery.controller

import io.exoquery.controller.jdbc.JdbcController
import io.exoquery.controller.jdbc.JdbcExecutionOptions

suspend fun <T> ControllerQuery<T>.runOn(ctx: JdbcController, options: JdbcExecutionOptions) = ctx.run(this, options)
suspend fun <T> ControllerQuery<T>.streamOn(ctx: JdbcController, options: JdbcExecutionOptions) = ctx.stream(this, options)
suspend fun <T> ControllerQuery<T>.runRawOn(ctx: JdbcController, options: JdbcExecutionOptions) = ctx.runRaw(this, options)
suspend fun ControllerAction.runOn(ctx: JdbcController, options: JdbcExecutionOptions) = ctx.run(this, options)
suspend fun <T> ControllerActionReturning<T>.runOn(ctx: JdbcController, options: JdbcExecutionOptions) = ctx.run(this, options)
suspend fun ControllerBatchAction.runOn(ctx: JdbcController, options: JdbcExecutionOptions) = ctx.run(this, options)
suspend fun <T> ControllerBatchActionReturning<T>.runOn(ctx: JdbcController, options: JdbcExecutionOptions) = ctx.run(this, options)
suspend fun <T> ControllerBatchActionReturning<T>.streamOn(ctx: JdbcController, options: JdbcExecutionOptions) = ctx.stream(this, options)
