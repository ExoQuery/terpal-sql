package io.exoquery.controller.r2dbc

import io.exoquery.controller.BasicEncoding
import io.exoquery.controller.EncoderAny
import io.exoquery.controller.JavaTimeEncoding
import io.exoquery.controller.JavaUuidEncoding
import io.exoquery.controller.SqlDecoder
import io.exoquery.controller.SqlEncoder
import io.exoquery.controller.SqlJson
import io.exoquery.controller.r2dbc.R2dbcTimeEncoding.NA
import io.r2dbc.spi.Connection
import io.r2dbc.spi.Row
import io.r2dbc.spi.Statement
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toJavaZoneId
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.datetime.toKotlinLocalTime
import java.sql.Types
import java.time.*
import java.util.*
import kotlin.reflect.KClass

// Note: R2DBC has no java.sql.Types. We keep an Int id for compatibility but do not use it.
class R2dbcEncoderAny<T: Any>(
  override val dataType: Int,
  override val type: KClass<T>,
  override val f: (R2dbcEncodingContext, T, Int) -> Unit,
): EncoderAny<T, Int, Connection, Statement>(
  dataType, type,
  { i, stmt, _ ->
    // Always use boxed reference types for nulls to satisfy R2DBC drivers (e.g., Postgres)
    stmt.bindNull(i, type.javaObjectType)
  },
  f
)

object R2dbcBasicEncoding: BasicEncoding<Connection, Statement, Row> {
  private const val NA = 0

  override val BooleanEncoder: SqlEncoder<Connection, Statement, Boolean> =
    R2dbcEncoderAny(NA, Boolean::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val ByteEncoder: SqlEncoder<Connection, Statement, Byte> =
    R2dbcEncoderAny(NA, Byte::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val CharEncoder: SqlEncoder<Connection, Statement, Char> =
    R2dbcEncoderAny(NA, Char::class) { ctx, v, i -> ctx.stmt.bind(i, v.toString()) }
  override val DoubleEncoder: SqlEncoder<Connection, Statement, Double> =
    R2dbcEncoderAny(NA, Double::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val FloatEncoder: SqlEncoder<Connection, Statement, Float> =
    R2dbcEncoderAny(NA, Float::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val IntEncoder: SqlEncoder<Connection, Statement, Int> =
    R2dbcEncoderAny(NA, Int::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val LongEncoder: SqlEncoder<Connection, Statement, Long> =
    R2dbcEncoderAny(NA, Long::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val ShortEncoder: SqlEncoder<Connection, Statement, Short> =
    R2dbcEncoderAny(NA, Short::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val StringEncoder: SqlEncoder<Connection, Statement, String> =
    R2dbcEncoderAny(NA, String::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val ByteArrayEncoder: SqlEncoder<Connection, Statement, ByteArray> =
    R2dbcEncoderAny(NA, ByteArray::class) { ctx, v, i -> ctx.stmt.bind(i, v) }

  override fun preview(index: Int, row: Row): String? =
    row.get(index)?.let { it.toString() }
  override fun isNull(index: Int, row: Row): Boolean =
    row.get(index) == null

  override val BooleanDecoder: SqlDecoder<Connection, Row, Boolean> =
    R2dbcDecoderAny(Boolean::class) { ctx, i -> ctx.row.get(i, java.lang.Boolean::class.java)?.booleanValue() }
  override val ByteDecoder: SqlDecoder<Connection, Row, Byte> =
    R2dbcDecoderAny(Byte::class) { ctx, i -> ctx.row.get(i, java.lang.Byte::class.java)?.toByte() }
  override val CharDecoder: SqlDecoder<Connection, Row, Char> =
    R2dbcDecoderAny(Char::class) { ctx, i -> ctx.row.get(i, String::class.java)?.let { it[0] } ?: Char.MIN_VALUE }
  override val DoubleDecoder: SqlDecoder<Connection, Row, Double> =
    R2dbcDecoderAny(Double::class) { ctx, i -> ctx.row.get(i, java.lang.Double::class.java)?.toDouble() }
  override val FloatDecoder: SqlDecoder<Connection, Row, Float> =
    R2dbcDecoderAny(Float::class) { ctx, i -> ctx.row.get(i, java.lang.Float::class.java)?.toFloat() }
  override val IntDecoder: SqlDecoder<Connection, Row, Int> =
    R2dbcDecoderAny(Int::class) { ctx, i -> ctx.row.get(i, java.lang.Integer::class.java)?.toInt() }
  override val LongDecoder: SqlDecoder<Connection, Row, Long> =
    R2dbcDecoderAny(Long::class) { ctx, i -> ctx.row.get(i, java.lang.Long::class.java)?.toLong() }
  override val ShortDecoder: SqlDecoder<Connection, Row, Short> =
    R2dbcDecoderAny(Short::class) { ctx, i -> ctx.row.get(i, java.lang.Short::class.java)?.toShort() }
  override val StringDecoder: SqlDecoder<Connection, Row, String> =
    R2dbcDecoderAny(String::class) { ctx, i -> ctx.row.get(i, String::class.java) }
  override val ByteArrayDecoder: SqlDecoder<Connection, Row, ByteArray> =
    R2dbcDecoderAny(ByteArray::class) { ctx, i -> ctx.row.get(i, ByteArray::class.java) }
}

private fun kotlinx.datetime.TimeZone.toJava(): TimeZone = TimeZone.getTimeZone(this.toJavaZoneId())

object R2dbcTimeEncoding: JavaTimeEncoding<Connection, Statement, Row> {
  private const val NA = 0

  // KMP datetime -> convert to java.time before binding
  override val LocalDateEncoder: SqlEncoder<Connection, Statement, kotlinx.datetime.LocalDate> =
    R2dbcEncoderAny(NA, kotlinx.datetime.LocalDate::class) { ctx, v, i ->
      ctx.stmt.bind(i, v.toJavaLocalDate())
    }
  override val LocalDateTimeEncoder: SqlEncoder<Connection, Statement, kotlinx.datetime.LocalDateTime> =
    R2dbcEncoderAny(NA, kotlinx.datetime.LocalDateTime::class) { ctx, v, i ->
      ctx.stmt.bind(i, v.toJavaLocalDateTime())
    }
  override val LocalTimeEncoder: SqlEncoder<Connection, Statement, kotlinx.datetime.LocalTime> =
    R2dbcEncoderAny(NA, kotlinx.datetime.LocalTime::class) { ctx, v, i ->
      ctx.stmt.bind(i, v.toJavaLocalTime())
    }
  override val InstantEncoder: SqlEncoder<Connection, Statement, kotlinx.datetime.Instant> =
    R2dbcEncoderAny(NA, kotlinx.datetime.Instant::class) { ctx, v, i ->
      ctx.stmt.bind(i, v.toJavaInstant())
    }

  // Java time types can be bound directly
  override val JLocalDateEncoder: SqlEncoder<Connection, Statement, LocalDate> =
    R2dbcEncoderAny(NA, LocalDate::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val JLocalTimeEncoder: SqlEncoder<Connection, Statement, LocalTime> =
    R2dbcEncoderAny(NA, LocalTime::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val JLocalDateTimeEncoder: SqlEncoder<Connection, Statement, LocalDateTime> =
    R2dbcEncoderAny(NA, LocalDateTime::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val JZonedDateTimeEncoder: SqlEncoder<Connection, Statement, ZonedDateTime> =
    R2dbcEncoderAny(NA, ZonedDateTime::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val JInstantEncoder: SqlEncoder<Connection, Statement, Instant> =
    R2dbcEncoderAny(NA, Instant::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val JOffsetTimeEncoder: SqlEncoder<Connection, Statement, OffsetTime> =
    R2dbcEncoderAny(NA, OffsetTime::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val JOffsetDateTimeEncoder: SqlEncoder<Connection, Statement, OffsetDateTime> =
    R2dbcEncoderAny(NA, OffsetDateTime::class) { ctx, v, i -> ctx.stmt.bind(i, v) }

  // java.util.Date -> bind as Instant (supported type)
  override val JDateEncoder: SqlEncoder<Connection, Statement, Date> =
    R2dbcEncoderAny(NA, Date::class) { ctx, v, i -> ctx.stmt.bind(i, Instant.ofEpochMilli(v.getTime())) }

  // KMP datetime decoders via java.time
  override val LocalDateDecoder: SqlDecoder<Connection, Row, kotlinx.datetime.LocalDate> =
    R2dbcDecoderAny(kotlinx.datetime.LocalDate::class) { ctx, i -> ctx.row.get(i, LocalDate::class.java)?.toKotlinLocalDate() }
  override val LocalDateTimeDecoder: SqlDecoder<Connection, Row, kotlinx.datetime.LocalDateTime> =
    R2dbcDecoderAny(kotlinx.datetime.LocalDateTime::class) { ctx, i -> ctx.row.get(i, LocalDateTime::class.java)?.toKotlinLocalDateTime() }
  override val LocalTimeDecoder: SqlDecoder<Connection, Row, kotlinx.datetime.LocalTime> =
    R2dbcDecoderAny(kotlinx.datetime.LocalTime::class) { ctx, i -> ctx.row.get(i, LocalTime::class.java)?.toKotlinLocalTime() }
  override val InstantDecoder: SqlDecoder<Connection, Row, kotlinx.datetime.Instant> =
    R2dbcDecoderAny(kotlinx.datetime.Instant::class) { ctx, i -> ctx.row.get(i, OffsetDateTime::class.java)?.toInstant()?.toKotlinInstant() }

  // Java time decoders
  override val JLocalDateDecoder: SqlDecoder<Connection, Row, LocalDate> =
    R2dbcDecoderAny(LocalDate::class) { ctx, i -> ctx.row.get(i, LocalDate::class.java) }
  override val JLocalTimeDecoder: SqlDecoder<Connection, Row, LocalTime> =
    R2dbcDecoderAny(LocalTime::class) { ctx, i -> ctx.row.get(i, LocalTime::class.java) }
  override val JLocalDateTimeDecoder: SqlDecoder<Connection, Row, LocalDateTime> =
    R2dbcDecoderAny(LocalDateTime::class) { ctx, i -> ctx.row.get(i, LocalDateTime::class.java) }
  override val JZonedDateTimeDecoder: SqlDecoder<Connection, Row, ZonedDateTime> =
    R2dbcDecoderAny(ZonedDateTime::class) { ctx, i -> ctx.row.get(i, OffsetDateTime::class.java)?.toZonedDateTime() }
  override val JInstantDecoder: SqlDecoder<Connection, Row, Instant> =
    R2dbcDecoderAny(Instant::class) { ctx, i -> ctx.row.get(i, OffsetDateTime::class.java)?.toInstant() }
  override val JOffsetTimeDecoder: SqlDecoder<Connection, Row, OffsetTime> =
    R2dbcDecoderAny(OffsetTime::class) { ctx, i -> ctx.row.get(i, OffsetTime::class.java) }
  override val JOffsetDateTimeDecoder: SqlDecoder<Connection, Row, OffsetDateTime> =
    R2dbcDecoderAny(OffsetDateTime::class) { ctx, i -> ctx.row.get(i, OffsetDateTime::class.java) }

  // java.util.Date from Instant
  override val JDateDecoder: SqlDecoder<Connection, Row, Date> =
    R2dbcDecoderAny(Date::class) { ctx, i -> ctx.row.get(i, Instant::class.java)?.let { Date.from(it) } }
}

object R2dbcUuidEncoding: JavaUuidEncoding<Connection, Statement, Row> {
  private const val NA = 0

  override val JUuidEncoder: SqlEncoder<Connection, Statement, UUID> =
    R2dbcEncoderAny(NA, UUID::class) { ctx, v, i -> ctx.stmt.bind(i, v) }

  override val JUuidDecoder: SqlDecoder<Connection, Row, UUID> =
    R2dbcDecoderAny(UUID::class) { ctx, i -> ctx.row.get(i, UUID::class.java) }
}

object R2dbcAdditionalEncoding {
  private const val NA = 0

  val BigDecimalEncoder: R2dbcEncoderAny<java.math.BigDecimal> =
    R2dbcEncoderAny(NA, java.math.BigDecimal::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  val BigDecimalDecoder: R2dbcDecoderAny<java.math.BigDecimal> =
    R2dbcDecoderAny(java.math.BigDecimal::class) { ctx, i -> ctx.row.get(i, java.math.BigDecimal::class.java) }
}

object R2dbcEncoders {
  @Suppress("UNCHECKED_CAST")
  val encoders: Set<SqlEncoder<Connection, Statement, out Any>> = setOf(
    R2dbcBasicEncoding.BooleanEncoder,
    R2dbcBasicEncoding.ByteEncoder,
    R2dbcBasicEncoding.CharEncoder,
    R2dbcBasicEncoding.DoubleEncoder,
    R2dbcBasicEncoding.FloatEncoder,
    R2dbcBasicEncoding.IntEncoder,
    R2dbcBasicEncoding.LongEncoder,
    R2dbcBasicEncoding.ShortEncoder,
    R2dbcBasicEncoding.StringEncoder,
    R2dbcBasicEncoding.ByteArrayEncoder,

    R2dbcTimeEncoding.LocalDateEncoder,
    R2dbcTimeEncoding.LocalDateTimeEncoder,
    R2dbcTimeEncoding.LocalTimeEncoder,
    R2dbcTimeEncoding.InstantEncoder,
    R2dbcTimeEncoding.JLocalDateEncoder,
    R2dbcTimeEncoding.JLocalTimeEncoder,
    R2dbcTimeEncoding.JLocalDateTimeEncoder,
    R2dbcTimeEncoding.JZonedDateTimeEncoder,
    R2dbcTimeEncoding.JInstantEncoder,
    R2dbcTimeEncoding.JOffsetTimeEncoder,
    R2dbcTimeEncoding.JOffsetDateTimeEncoder,
    R2dbcTimeEncoding.JDateEncoder,
    R2dbcUuidEncoding.JUuidEncoder,

    R2dbcAdditionalEncoding.BigDecimalEncoder
  )
}
