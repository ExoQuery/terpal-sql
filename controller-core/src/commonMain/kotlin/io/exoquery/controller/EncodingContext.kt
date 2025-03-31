package io.exoquery.controller

import kotlinx.datetime.TimeZone

data class QueryDebugInfo(val query: String)

open class EncodingContext<Session, Stmt>(open val session: Session, open val stmt: Stmt, open val timeZone: TimeZone)
open class DecodingContext<Session, Row>(
  open val session: Session,
  open val row: Row,
  open val timeZone: TimeZone,
  val columnInfos: List<ColumnInfo>?,
  open val debugInfo: QueryDebugInfo?
)
