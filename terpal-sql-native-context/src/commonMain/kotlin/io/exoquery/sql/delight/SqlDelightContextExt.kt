package io.exoquery.sql.delight

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.exoquery.sql.Action
import io.exoquery.sql.Query

fun <T> Query<T>.runOnDelight(ctx: SqlDelightContext, sqlDelightId: Int? = null) = ctx.runToResult(this, sqlDelightId)
fun Action.runOnDelight(ctx: SqlDelightContext, sqlDelightId: Int? = null) = ctx.runToResult(this, sqlDelightId)

fun <T> Query<T>.runOnDriver(ctx: NativeSqliteDriver, sqlDelightId: Int? = null) = SqlDelightContext(ctx).runToResult(this, sqlDelightId)
fun Action.runOnDriver(ctx: NativeSqliteDriver, sqlDelightId: Int? = null) = SqlDelightContext(ctx).runToResult(this, sqlDelightId)
