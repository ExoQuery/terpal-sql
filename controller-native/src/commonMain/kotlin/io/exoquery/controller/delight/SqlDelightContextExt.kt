package io.exoquery.controller.delight

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.exoquery.controller.Action
import io.exoquery.controller.Query

fun <T> Query<T>.runOnDelight(ctx: SqlDelightContext, sqlDelightId: Int? = null) = ctx.runToResult(this, sqlDelightId)
fun Action.runOnDelight(ctx: SqlDelightContext, sqlDelightId: Int? = null) = ctx.runToResult(this, sqlDelightId)

fun <T> Query<T>.runOnDriver(ctx: NativeSqliteDriver, sqlDelightId: Int? = null) = SqlDelightContext(ctx).runToResult(this, sqlDelightId)
fun Action.runOnDriver(ctx: NativeSqliteDriver, sqlDelightId: Int? = null) = SqlDelightContext(ctx).runToResult(this, sqlDelightId)
