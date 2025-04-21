package io.exoquery.controller.delight

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.exoquery.controller.ControllerAction
import io.exoquery.controller.ControllerQuery

fun <T> ControllerQuery<T>.runOnDelight(ctx: SqlDelightController, sqlDelightId: Int? = null) = ctx.runToResult(this, sqlDelightId)
fun ControllerAction.runOnDelight(ctx: SqlDelightController, sqlDelightId: Int? = null) = ctx.runToResult(this, sqlDelightId)

fun <T> ControllerQuery<T>.runOnDriver(ctx: NativeSqliteDriver, sqlDelightId: Int? = null) = SqlDelightController(ctx).runToResult(this, sqlDelightId)
fun ControllerAction.runOnDriver(ctx: NativeSqliteDriver, sqlDelightId: Int? = null) = SqlDelightController(ctx).runToResult(this, sqlDelightId)
