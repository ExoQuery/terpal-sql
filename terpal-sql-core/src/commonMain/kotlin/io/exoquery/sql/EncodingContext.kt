package io.exoquery.sql

import kotlinx.datetime.TimeZone

open class EncodingContext<Session, Stmt>(open val session: Session, open val stmt: Stmt, open val timeZone: TimeZone)
open class DecodingContext<Session, Row>(open val session: Session, open val row: Row, open val timeZone: TimeZone)
