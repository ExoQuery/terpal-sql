package io.exoquery.controller

import kotlinx.datetime.TimeZone

data class QueryDebugInfo(val query: String)

open class EncodingContext<Session, Stmt>(
  open val session: Session,
  open val stmt: Stmt,
  open val timeZone: TimeZone,
  open val startingIndex: StartingIndex,
  open val dbTypeIsRelevant: Boolean
)
open class DecodingContext<Session, Row>(
  open val session: Session,
  open val row: Row,
  open val timeZone: TimeZone,
  open val startingIndex: StartingIndex,
  open val columnInfos: List<ColumnInfo>?,
  open val debugInfo: QueryDebugInfo?
) {
  /**
   * Get the column info for the given index. The index is 1-based since this is the general case for database row-sets.
   * (TODO what about the android result-set)
   */
  fun columnInfo(index: Int): ColumnInfo? =
    columnInfos?.get(index-startingIndex.value)

  fun columnInfoSafe(index: Int): ColumnInfo? =
    try {
      columnInfo(index)
    } catch (ex: IndexOutOfBoundsException) {
      null
    }
}
