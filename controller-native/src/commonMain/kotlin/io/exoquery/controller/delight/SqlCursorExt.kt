package io.exoquery.controller.delight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun <T> SqlCursor.awaitAll(mapper: (SqlCursor) -> T): QueryResult<List<T>> {
  val cursor = this
  val first = cursor.next()
  val result = mutableListOf<T>()

  // If the cursor isn't async, we want to preserve the blocking semantics and execute it synchronously
  return when (first) {
    is QueryResult.AsyncValue -> {
      QueryResult.AsyncValue {
        if (first.await()) result.add(mapper(cursor)) else return@AsyncValue result
        while (cursor.next().await()) result.add(mapper(cursor))
        result
      }
    }

    is QueryResult.Value -> {
      if (first.value)
        result.add(mapper(cursor))
      else
        return QueryResult.Value(result)

      while (cursor.next().value) result.add(mapper(cursor))
      QueryResult.Value(result.toList())
    }
  }
}
