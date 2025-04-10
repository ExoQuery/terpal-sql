package io.exoquery.controller

import io.exoquery.controller.jdbc.JdbcController
import io.exoquery.controller.jdbc.JdbcExecutionOptions
import kotlinx.coroutines.invoke

suspend fun <T> Query<T>.runOn(ctx: JdbcController, options: JdbcExecutionOptions) = ctx.run(this, options)
suspend fun <T> Query<T>.streamOn(ctx: JdbcController, options: JdbcExecutionOptions) = ctx.stream(this, options)
suspend fun <T> Query<T>.runRawOn(ctx: JdbcController, options: JdbcExecutionOptions) = ctx.runRaw(this, options)
suspend fun Action.runOn(ctx: JdbcController, options: JdbcExecutionOptions) = ctx.run(this, options)
suspend fun <T> ActionReturning<T>.runOn(ctx: JdbcController, options: JdbcExecutionOptions) = ctx.run(this, options)
suspend fun BatchAction.runOn(ctx: JdbcController, options: JdbcExecutionOptions) = ctx.run(this, options)
suspend fun <T> BatchActionReturning<T>.runOn(ctx: JdbcController, options: JdbcExecutionOptions) = ctx.run(this, options)
suspend fun <T> BatchActionReturning<T>.streamOn(ctx: JdbcController, options: JdbcExecutionOptions) = ctx.stream(this, options)
