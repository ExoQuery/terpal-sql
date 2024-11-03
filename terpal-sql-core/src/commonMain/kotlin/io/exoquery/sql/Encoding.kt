package io.exoquery.sql

import kotlin.reflect.KClass
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Instant

// TODO add this to SqlDecoder to denote possible list decoders
sealed interface DecoderType {
  data object Plain
  data class Array(val elementType: KClass<*>)
}

abstract class SqlDecoder<Session, Row, T> {
  abstract val type: KClass<*> // Don't want to force T to be non-nullable so using KClass instead of KClass<T>
  abstract fun decode(ctx: DecodingContext<Session, Row>, index: Int): T
  abstract fun asNullable(): SqlDecoder<Session, Row, T?>

  val id by lazy { Id(type) }
  override fun hashCode(): Int = id.hashCode()
  override fun equals(other: Any?): Boolean = other is SqlDecoder<*, *, *> && other.id == id

  companion object {
    data class Id(val type: KClass<*>)
  }
}

abstract class SqlEncoder<Session, Statement, T> {
  abstract val type: KClass<*>
  abstract fun encode(ctx: EncodingContext<Session, Statement>, value: T, index: Int): Unit
  abstract fun asNullable(): SqlEncoder<Session, Statement, T?>

  // Id should only be based on the type so that SqlDecoders composition works
  val id by lazy { Id(type) }
  override fun hashCode(): Int = id.hashCode()
  override fun equals(other: Any?): Boolean = other is SqlEncoder<*, *, *> && other.id == id

  companion object {
    data class Id(val type: KClass<*>)
  }
}

// Used by the PreparedStatementEncoder
interface ApiEncoders<Session, Stmt> {
  val BooleanEncoder: SqlEncoder<Session, Stmt, Boolean>
  val ByteEncoder: SqlEncoder<Session, Stmt, Byte>
  val CharEncoder: SqlEncoder<Session, Stmt, Char>
  val DoubleEncoder: SqlEncoder<Session, Stmt, Double>
  val FloatEncoder: SqlEncoder<Session, Stmt, Float>
  val IntEncoder: SqlEncoder<Session, Stmt, Int>
  val LongEncoder: SqlEncoder<Session, Stmt, Long>
  val ShortEncoder: SqlEncoder<Session, Stmt, Short>
  val StringEncoder: SqlEncoder<Session, Stmt, String>
  val ByteArrayEncoder: SqlEncoder<Session, Stmt, ByteArray>
}
// Used by the RowDecoder
interface ApiDecoders<Session, Row> {
  val BooleanDecoder: SqlDecoder<Session, Row, Boolean>
  val ByteDecoder: SqlDecoder<Session, Row, Byte>
  val CharDecoder: SqlDecoder<Session, Row, Char>
  val DoubleDecoder: SqlDecoder<Session, Row, Double>
  val FloatDecoder: SqlDecoder<Session, Row, Float>
  val IntDecoder: SqlDecoder<Session, Row, Int>
  val LongDecoder: SqlDecoder<Session, Row, Long>
  val ShortDecoder: SqlDecoder<Session, Row, Short>
  val StringDecoder: SqlDecoder<Session, Row, String>
  val ByteArrayDecoder: SqlDecoder<Session, Row, ByteArray>

  abstract fun isNull(index: Int, row: Row): Boolean
  abstract fun preview(index: Int, row: Row): String?
}

data class SqlJson(val value: String)

interface BasicEncoding<Session, Stmt, Row>:
  ApiEncoders<Session, Stmt>,
  ApiDecoders<Session, Row>

interface TimeEncoding<Session, Stmt, Row> {
  val LocalDateEncoder: SqlEncoder<Session, Stmt, LocalDate>
  val LocalDateTimeEncoder: SqlEncoder<Session, Stmt, LocalDateTime>
  val LocalTimeEncoder: SqlEncoder<Session, Stmt, LocalTime>
  val InstantEncoder: SqlEncoder<Session, Stmt, Instant>

  val LocalDateDecoder: SqlDecoder<Session, Row, LocalDate>
  val LocalDateTimeDecoder: SqlDecoder<Session, Row, LocalDateTime>
  val LocalTimeDecoder: SqlDecoder<Session, Row, LocalTime>
  val InstantDecoder: SqlDecoder<Session, Row, Instant>
}

interface SqlEncoding<Session, Stmt, Row>:
  BasicEncoding<Session, Stmt, Row>,
  TimeEncoding<Session, Stmt, Row> {

  fun computeEncoders(): Set<SqlEncoder<Session, Stmt, out Any>> =
    setOf(
      BooleanEncoder,
      ByteEncoder,
      CharEncoder,
      DoubleEncoder,
      FloatEncoder,
      IntEncoder,
      LongEncoder,
      ShortEncoder,
      StringEncoder,
      ByteArrayEncoder,
      LocalDateEncoder,
      LocalDateTimeEncoder,
      LocalTimeEncoder,
      InstantEncoder
    )

  fun computeDecoders(): Set<SqlDecoder<Session, Row, out Any>> =
    setOf(
      BooleanDecoder,
      ByteDecoder,
      CharDecoder,
      DoubleDecoder,
      FloatDecoder,
      IntDecoder,
      LongDecoder,
      ShortDecoder,
      StringDecoder,
      ByteArrayDecoder,
      LocalDateDecoder,
      LocalDateTimeDecoder,
      LocalTimeDecoder,
      InstantDecoder
    )
}
