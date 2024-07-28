package io.exoquery.sql

import java.time.*
import java.math.BigDecimal
import java.util.UUID

interface JavaTimeEncoding<Session, Stmt, Row>: TimeEncoding<Session, Stmt, Row> {
  val JLocalDateEncoder: SqlEncoder<Session, Stmt, LocalDate>
  val JLocalTimeEncoder: SqlEncoder<Session, Stmt, LocalTime>
  val JLocalDateTimeEncoder: SqlEncoder<Session, Stmt, LocalDateTime>
  val JZonedDateTimeEncoder: SqlEncoder<Session, Stmt, ZonedDateTime>
  val JInstantEncoder: SqlEncoder<Session, Stmt, Instant>
  val JOffsetTimeEncoder: SqlEncoder<Session, Stmt, OffsetTime>
  val JOffsetDateTimeEncoder: SqlEncoder<Session, Stmt, OffsetDateTime>

  val JLocalDateDecoder: SqlDecoder<Session, Row, LocalDate>
  val JLocalTimeDecoder: SqlDecoder<Session, Row, LocalTime>
  val JLocalDateTimeDecoder: SqlDecoder<Session, Row, LocalDateTime>
  val JZonedDateTimeDecoder: SqlDecoder<Session, Row, ZonedDateTime>
  val JInstantDecoder: SqlDecoder<Session, Row, Instant>
  val JOffsetTimeDecoder: SqlDecoder<Session, Row, OffsetTime>
  val JOffsetDateTimeDecoder: SqlDecoder<Session, Row, OffsetDateTime>
}

interface JavaUuidEncoding<Session, Stmt, Row> {
  val JUuidEncoder: SqlEncoder<Session, Stmt, UUID>
  val JUuidDecoder: SqlDecoder<Session, Row, UUID>
}

interface JavaLegacyDateEncoding<Session, Stmt, Row> {
  val DateEncoder: SqlEncoder<Session, Stmt, java.util.Date>
  val DateDecoder: SqlDecoder<Session, Row, java.util.Date>
}

interface JavaBigDecimalEncoding<Session, Stmt, Row> {
  val BigDecimalEncoder: SqlEncoder<Session, Stmt, BigDecimal>
  val BigDecimalDecoder: SqlDecoder<Session, Row, BigDecimal>
}

interface JavaSqlEncoding<Session, Stmt, Row>:
  SqlEncoding<Session, Stmt, Row>,
  JavaTimeEncoding<Session, Stmt, Row>,
  JavaLegacyDateEncoding<Session, Stmt, Row>,
  JavaBigDecimalEncoding<Session, Stmt, Row>,
  JavaUuidEncoding<Session, Stmt, Row> {

  override fun computeEncoders(): Set<SqlEncoder<Session, Stmt, out Any>> =
      super.computeEncoders() +
      setOf(
        JUuidEncoder,
        BigDecimalEncoder,
        DateEncoder,
        JLocalDateEncoder,
        JLocalTimeEncoder,
        JLocalDateTimeEncoder,
        JZonedDateTimeEncoder,
        JInstantEncoder,
        JOffsetTimeEncoder,
        JOffsetDateTimeEncoder
      )

  override fun computeDecoders(): Set<SqlDecoder<Session, Row, out Any>> =
      super.computeDecoders() +
      setOf(
        JUuidDecoder,
        BigDecimalDecoder,
        DateDecoder,
        JLocalDateDecoder,
        JLocalTimeDecoder,
        JLocalDateTimeDecoder,
        JZonedDateTimeDecoder,
        JInstantDecoder,
        JOffsetTimeDecoder,
        JOffsetDateTimeDecoder
      )
}
