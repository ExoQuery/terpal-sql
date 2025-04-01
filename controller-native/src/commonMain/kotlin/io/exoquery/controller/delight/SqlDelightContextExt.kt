package io.exoquery.controller.delight

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.exoquery.controller.Action
import io.exoquery.controller.Query

fun <T> Query<T>.runOnDelight(ctx: SqlDelightController, sqlDelightId: Int? = null) = ctx.runToResult(this, sqlDelightId)
fun Action.runOnDelight(ctx: SqlDelightController, sqlDelightId: Int? = null) = ctx.runToResult(this, sqlDelightId)

fun <T> Query<T>.runOnDriver(ctx: NativeSqliteDriver, sqlDelightId: Int? = null) = SqlDelightController(ctx).runToResult(this, sqlDelightId)
fun Action.runOnDriver(ctx: NativeSqliteDriver, sqlDelightId: Int? = null) = SqlDelightController(ctx).runToResult(this, sqlDelightId)
