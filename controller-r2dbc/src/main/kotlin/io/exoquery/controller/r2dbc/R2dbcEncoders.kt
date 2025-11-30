package io.exoquery.controller.r2dbc

import io.exoquery.controller.BasicEncoding
import io.exoquery.controller.ControllerError
import io.exoquery.controller.DecoderAny
import io.exoquery.controller.EncoderAny
import io.exoquery.controller.JavaTimeEncoding
import io.exoquery.controller.JavaUuidEncoding
import io.exoquery.controller.SqlDecoder
import io.exoquery.controller.SqlEncoder
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
import java.time.*
import java.util.*
import kotlin.reflect.KClass

typealias R2dbcEncoder<T> = EncoderAny<T, Int, Connection, Statement>

// Note: R2DBC has no java.sql.Types. We keep an Int id for compatibility but do not use it.
open class R2dbcEncoderAny<T: Any>(
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

private const val NA = 0

object R2dbcBasicEncoding: R2dbcBasicEncodingBase()

object R2dbcBasicEncodingH2: R2dbcBasicEncodingBase() {
  override val ByteDecoder: R2dbcDecoder<Byte> =
    R2dbcDecoderAny(Byte::class) { ctx, i -> ctx.row.get(i, java.lang.Short::class.java)?.toByte() }

  override val FloatDecoder: R2dbcDecoder<Float> =
    R2dbcDecoderAny(Float::class) { ctx, i ->
      when (val value = ctx.row.get(i)) {
        null -> null
        is Float -> value
        is Double -> value.toFloat()
        is String -> value.toFloat()
        else -> throw ControllerError.DecodingError(
          "Cannot decode H2 FLOAT column at index $i: unsupported underlying type ${value::class.simpleName}"
        )
      }
    }

  override val IntDecoder: R2dbcDecoder<Int> =
    R2dbcDecoderAny(Int::class) { ctx, i ->
      when (val value = ctx.row.get(i)) {
        null -> null
        is Int -> value.toInt()
        is Long -> value.toInt()
        is Short -> value.toInt()
        is String -> value.toInt()
        else -> throw ControllerError.DecodingError(
          "Cannot decode H2 INT column at index $i: unsupported underlying type ${value?.let { it::class.simpleName }}"
        )
      }
    }

  override val LongDecoder: R2dbcDecoder<Long> =
    R2dbcDecoderAny(Long::class) { ctx, i ->
      when (val value = ctx.row.get(i)) {
        null -> null
        is Long -> value.toLong()
        is Int -> value.toLong()
        is Short -> value.toLong()
        is String -> value.toLong()
        else -> throw ControllerError.DecodingError(
          "Cannot decode H2 BIGINT column at index $i: unsupported underlying type ${value?.let { it::class.simpleName }}"
        )
      }
    }

  override val ShortDecoder: R2dbcDecoder<Short> =
    R2dbcDecoderAny(Short::class) { ctx, i ->
      when (val value = ctx.row.get(i)) {
        null -> null
        is Short -> value.toShort()
        is Int -> value.toShort()
        is Long -> value.toShort()
        is String -> value.toShort()
        else -> throw ControllerError.DecodingError(
          "Cannot decode H2 SMALLINT column at index $i: unsupported underlying type ${value?.let { it::class.simpleName }}"
        )
      }
    }
}

// Oracle has this crazy behavior where empty strings are treated as NULLs in R2DBC. Need to account for that by converting to "" when
// the get method returns null. Need to account for this behavior by turning null values from getString into empty strings.
// The get(i, String::class.java) function is used in the StringDecoder as well as the CharDecoder.
// Note that this will not mess up the functionality of a Nullable decoder (i.e. the result of R2dbcEncoder.asNullable()) because the
// nullable decoder first checks the row using row.get(index) == null before calling the non-nullable decoder. If the row is null then the non-null
// decoder is not invoked so we would not care about it converting a `null` value to an empty String either way.
// This same logic applies to the ByteArrayDecoder as well.
// More oracle crazy behavior that requires encoding/decoding booleans as ints (0/1).
object R2dbcBasicEncodingOracle: R2dbcBasicEncodingBase() {
  override val CharDecoder: R2dbcDecoder<Char> =
    R2dbcDecoderAny(Char::class) { ctx, i -> ctx.row.get(i, String::class.java)?.let { it[0] } ?: Char.MIN_VALUE }
  override val StringDecoder: R2dbcDecoder<String> =
    R2dbcDecoderAny(String::class) { ctx, i -> ctx.row.get(i, String::class.java) ?: "" }
  override val ByteArrayDecoder: R2dbcDecoder<ByteArray> =
    R2dbcDecoderAny(ByteArray::class) { ctx, i -> ctx.row.get(i, ByteArray::class.java) ?: byteArrayOf() }

  // More oracle crazy behavior that requires encoding booleans as ints
  //override val BooleanEncoder: R2dbcEncoderAny<Boolean> =
  //  R2dbcEncoderAny(NA, Boolean::class) { ctx, v, i -> ctx.stmt.bind(i, if (v) 1 else 0) }
  //override val BooleanDecoder: R2dbcDecoder<Boolean> =
  //  R2dbcDecoderAny(Boolean::class) { ctx, i -> ctx.row.get(i, java.lang.Integer::class.java)?.let { it == 1 } }
}

abstract class R2dbcBasicEncodingBase: BasicEncoding<Connection, Statement, Row> {
  override val BooleanEncoder: R2dbcEncoderAny<Boolean> =
    R2dbcEncoderAny(NA, Boolean::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val ByteEncoder: R2dbcEncoderAny<Byte> =
    R2dbcEncoderAny(NA, Byte::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val CharEncoder: R2dbcEncoderAny<Char> =
    R2dbcEncoderAny(NA, Char::class) { ctx, v, i -> ctx.stmt.bind(i, v.toString()) }
  override val DoubleEncoder: R2dbcEncoderAny<Double> =
    R2dbcEncoderAny(NA, Double::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val FloatEncoder: R2dbcEncoderAny<Float> =
    R2dbcEncoderAny(NA, Float::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val IntEncoder: R2dbcEncoderAny<Int> =
    R2dbcEncoderAny(NA, Int::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val LongEncoder: R2dbcEncoderAny<Long> =
    R2dbcEncoderAny(NA, Long::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val ShortEncoder: R2dbcEncoderAny<Short> =
    R2dbcEncoderAny(NA, Short::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val StringEncoder: R2dbcEncoderAny<String> =
    R2dbcEncoderAny(NA, String::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val ByteArrayEncoder: R2dbcEncoderAny<ByteArray> =
    R2dbcEncoderAny(NA, ByteArray::class) { ctx, v, i -> ctx.stmt.bind(i, v) }

  override fun preview(index: Int, row: Row): String? =
    row.get(index)?.let { it.toString() }
  override fun isNull(index: Int, row: Row): Boolean =
    row.get(index) == null

  override val BooleanDecoder: R2dbcDecoder<Boolean> =
    R2dbcDecoderAny(Boolean::class) { ctx, i -> ctx.row.get(i, java.lang.Boolean::class.java)?.booleanValue() }
  override val ByteDecoder: R2dbcDecoder<Byte> =
    R2dbcDecoderAny(Byte::class) { ctx, i -> ctx.row.get(i, java.lang.Byte::class.java)?.toByte() }
  override val CharDecoder: R2dbcDecoder<Char> =
    R2dbcDecoderAny(Char::class) { ctx, i -> ctx.row.get(i, String::class.java)?.let { it[0] } ?: Char.MIN_VALUE }
  override val DoubleDecoder: R2dbcDecoder<Double> =
    R2dbcDecoderAny(Double::class) { ctx, i -> ctx.row.get(i, java.lang.Double::class.java)?.toDouble() }
  override val FloatDecoder: R2dbcDecoder<Float> =
    R2dbcDecoderAny(Float::class) { ctx, i -> ctx.row.get(i, java.lang.Float::class.java)?.toFloat() }
  override val IntDecoder: R2dbcDecoder<Int> =
    R2dbcDecoderAny(Int::class) { ctx, i -> ctx.row.get(i, java.lang.Integer::class.java)?.toInt() }
  override val LongDecoder: R2dbcDecoder<Long> =
    R2dbcDecoderAny(Long::class) { ctx, i -> ctx.row.get(i, java.lang.Long::class.java)?.toLong() }
  override val ShortDecoder: R2dbcDecoder<Short> =
    R2dbcDecoderAny(Short::class) { ctx, i -> ctx.row.get(i, java.lang.Short::class.java)?.toShort() }
  override val StringDecoder: R2dbcDecoder<String> =
    R2dbcDecoderAny(String::class) { ctx, i -> ctx.row.get(i, String::class.java) }
  override val ByteArrayDecoder: R2dbcDecoder<ByteArray> =
    R2dbcDecoderAny(ByteArray::class) { ctx, i -> ctx.row.get(i, ByteArray::class.java) }
}

private fun kotlinx.datetime.TimeZone.toJava(): TimeZone = TimeZone.getTimeZone(this.toJavaZoneId())

object R2dbcTimeEncoding: R2dbcTimeEncodingBase()

object R2dbcTimeEncodingH2: R2dbcTimeEncodingBase() {
  /** java.util.Date -> bind as Instant (supported type)
   * original behavior is to assume the field actually supports timestamp with timezone
   */
  override val JDateEncoder: R2dbcEncoder<Date> =
    JLocalDateTimeEncoder.contramap { v: Date ->
      Instant.ofEpochMilli(v.time).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }

  /** java.util.Date from LocalDateTime
   * H2 R2DBC doesn't support Instant directly for TIMESTAMP columns, so we decode via LocalDateTime
   */
  override val JDateDecoder: R2dbcDecoder<Date> =
    JLocalDateTimeDecoder.map { localDateTime ->
      Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant())
    }
}

object R2dbcTimeEncodingSqlServer: R2dbcTimeEncodingBase() {
  // java.util.Date -> bind as OffsetDateTime (supported by SQL Server and Postgres)
  override val JDateEncoder: R2dbcEncoder<Date> =
    JOffsetDateTimeEncoder.contramap { v: Date ->
      OffsetDateTime.ofInstant(Instant.ofEpochMilli(v.time), ctx.timeZone.toJavaZoneId())
    }

  override val JDateDecoder: R2dbcDecoder<Date> =
    JOffsetDateTimeDecoder.map { odt: OffsetDateTime ->
      Date.from(odt.toInstant())
    }

  // SQL Server does not support Instant binding, so bind as OffsetDateTime in UTC
  override val InstantEncoder: R2dbcEncoder<kotlinx.datetime.Instant> =
    JOffsetDateTimeEncoder.contramap { v: kotlinx.datetime.Instant ->
      OffsetDateTime.ofInstant(v.toJavaInstant(), ZoneOffset.UTC)
    }

  override val InstantDecoder: R2dbcDecoder<kotlinx.datetime.Instant> =
    JOffsetDateTimeDecoder.map { odt: OffsetDateTime ->
      odt.toInstant().toKotlinInstant()
    }

  override val JInstantEncoder: R2dbcEncoder<Instant> =
    JOffsetDateTimeEncoder.contramap { v: Instant ->
      OffsetDateTime.ofInstant(v, ZoneOffset.UTC)
    }

  override val JInstantDecoder: R2dbcDecoder<Instant> =
    JOffsetDateTimeDecoder.map { odt: OffsetDateTime ->
      odt.toInstant()
    }

  // Convert OffsetTime -> OffsetDateTime on a fixed date (SQL Server DATETIMEOFFSET)
  override val JOffsetTimeEncoder: R2dbcEncoder<OffsetTime> =
    JOffsetDateTimeEncoder.contramap { v: OffsetTime ->
      OffsetDateTime.of(LocalDate.of(1970, 1, 1), v.toLocalTime(), v.offset)
    }

  override val JOffsetTimeDecoder: R2dbcDecoder<OffsetTime> =
    JOffsetDateTimeDecoder.map { odt: OffsetDateTime ->
      odt.toOffsetTime()
    }
}



object R2dbcTimeEncodingOracle: R2dbcTimeEncodingBase() {
  // Oracle supports binding via a OffsetDateTime but ironically, it's TIMESTAMP does not have a TimeZone. Therefore
  // when the row.get happens the OffsetDateTime translates as UTC! The simplest way to deal with that is setting it initially to UTC
  override val JDateEncoder: R2dbcEncoder<Date> =
    JOffsetDateTimeEncoder.contramap { v: Date ->
      OffsetDateTime.ofInstant(Instant.ofEpochMilli(v.time), ZoneOffset.UTC)
    }

  override val JDateDecoder: R2dbcDecoder<Date> =
    JOffsetDateTimeDecoder.map { odt: OffsetDateTime ->
      Date.from(odt.toInstant())
    }

  // Oracle does not support Instant binding, so bind as OffsetDateTime in UTC
  override val InstantEncoder: R2dbcEncoder<kotlinx.datetime.Instant> =
    JOffsetDateTimeEncoder.contramap { v: kotlinx.datetime.Instant ->
      OffsetDateTime.ofInstant(v.toJavaInstant(), ZoneOffset.UTC)
    }

  override val InstantDecoder: R2dbcDecoder<kotlinx.datetime.Instant> =
    JOffsetDateTimeDecoder.map { odt: OffsetDateTime ->
      odt.toInstant().toKotlinInstant()
    }

  // Oracle R2DBC does not support ZonedDateTime directly, convert to OffsetDateTime
  override val JZonedDateTimeEncoder: R2dbcEncoder<ZonedDateTime> =
    JOffsetDateTimeEncoder.contramap { v: ZonedDateTime ->
      v.toOffsetDateTime()
    }

  override val JZonedDateTimeDecoder: R2dbcDecoder<ZonedDateTime> =
    JOffsetDateTimeDecoder.map { odt: OffsetDateTime ->
      odt.toZonedDateTime()
    }

  override val JInstantEncoder: R2dbcEncoder<Instant> =
    JOffsetDateTimeEncoder.contramap { v: Instant ->
      OffsetDateTime.ofInstant(v, ZoneOffset.UTC)
    }

  override val JInstantDecoder: R2dbcDecoder<Instant> =
    JOffsetDateTimeDecoder.map { odt: OffsetDateTime ->
      odt.toInstant()
    }
}

abstract class R2dbcTimeEncodingBase: JavaTimeEncoding<Connection, Statement, Row> {

  // KMP datetime -> convert to java.time before binding
  override val LocalDateEncoder: R2dbcEncoder<kotlinx.datetime.LocalDate> =
    R2dbcEncoderAny(NA, kotlinx.datetime.LocalDate::class) { ctx, v, i ->
      ctx.stmt.bind(i, v.toJavaLocalDate())
    }
  override val LocalDateTimeEncoder: R2dbcEncoder<kotlinx.datetime.LocalDateTime> =
    R2dbcEncoderAny(NA, kotlinx.datetime.LocalDateTime::class) { ctx, v, i ->
      ctx.stmt.bind(i, v.toJavaLocalDateTime())
    }
  override val LocalTimeEncoder: R2dbcEncoder<kotlinx.datetime.LocalTime> =
    R2dbcEncoderAny(NA, kotlinx.datetime.LocalTime::class) { ctx, v, i ->
      ctx.stmt.bind(i, v.toJavaLocalTime())
    }
  override val InstantEncoder: R2dbcEncoder<kotlinx.datetime.Instant> =
    R2dbcEncoderAny(NA, kotlinx.datetime.Instant::class) { ctx, v, i ->
      ctx.stmt.bind(i, v.toJavaInstant())
    }

  // Java time types can be bound directly
  override val JLocalDateEncoder: R2dbcEncoder<LocalDate> =
    R2dbcEncoderAny(NA, LocalDate::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val JLocalTimeEncoder: R2dbcEncoder<LocalTime> =
    R2dbcEncoderAny(NA, LocalTime::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val JLocalDateTimeEncoder: R2dbcEncoder<LocalDateTime> =
    R2dbcEncoderAny(NA, LocalDateTime::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val JZonedDateTimeEncoder: R2dbcEncoder<ZonedDateTime> =
    R2dbcEncoderAny(NA, ZonedDateTime::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val JInstantEncoder: R2dbcEncoder<Instant> =
    R2dbcEncoderAny(NA, Instant::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val JOffsetTimeEncoder: R2dbcEncoder<OffsetTime> =
    R2dbcEncoderAny(NA, OffsetTime::class) { ctx, v, i -> ctx.stmt.bind(i, v) }
  override val JOffsetDateTimeEncoder: R2dbcEncoder<OffsetDateTime> =
    R2dbcEncoderAny(NA, OffsetDateTime::class) { ctx, v, i -> ctx.stmt.bind(i, v) }

  // KMP datetime decoders via java.time
  override val LocalDateDecoder: R2dbcDecoder<kotlinx.datetime.LocalDate> =
    R2dbcDecoderAny(kotlinx.datetime.LocalDate::class) { ctx, i -> ctx.row.get(i, LocalDate::class.java)?.toKotlinLocalDate() }
  override val LocalDateTimeDecoder: R2dbcDecoder<kotlinx.datetime.LocalDateTime> =
    R2dbcDecoderAny(kotlinx.datetime.LocalDateTime::class) { ctx, i -> ctx.row.get(i, LocalDateTime::class.java)?.toKotlinLocalDateTime() }
  override val LocalTimeDecoder: R2dbcDecoder<kotlinx.datetime.LocalTime> =
    R2dbcDecoderAny(kotlinx.datetime.LocalTime::class) { ctx, i -> ctx.row.get(i, LocalTime::class.java)?.toKotlinLocalTime() }
  override val InstantDecoder: R2dbcDecoder<kotlinx.datetime.Instant> =
    R2dbcDecoderAny(kotlinx.datetime.Instant::class) { ctx, i -> ctx.row.get(i, OffsetDateTime::class.java)?.toInstant()?.toKotlinInstant() }

  // Java time decoders
  override val JLocalDateDecoder: R2dbcDecoder<LocalDate> =
    R2dbcDecoderAny(LocalDate::class) { ctx, i -> ctx.row.get(i, LocalDate::class.java) }
  override val JLocalTimeDecoder: R2dbcDecoder<LocalTime> =
    R2dbcDecoderAny(LocalTime::class) { ctx, i -> ctx.row.get(i, LocalTime::class.java) }
  override val JLocalDateTimeDecoder: R2dbcDecoder<LocalDateTime> =
    R2dbcDecoderAny(LocalDateTime::class) { ctx, i -> ctx.row.get(i, LocalDateTime::class.java) }
  override val JZonedDateTimeDecoder: R2dbcDecoder<ZonedDateTime> =
    R2dbcDecoderAny(ZonedDateTime::class) { ctx, i -> ctx.row.get(i, OffsetDateTime::class.java)?.toZonedDateTime() }
  override val JInstantDecoder: R2dbcDecoder<Instant> =
    R2dbcDecoderAny(Instant::class) { ctx, i -> ctx.row.get(i, OffsetDateTime::class.java)?.toInstant() }
  override val JOffsetTimeDecoder: R2dbcDecoder<OffsetTime> =
    R2dbcDecoderAny(OffsetTime::class) { ctx, i -> ctx.row.get(i, OffsetTime::class.java) }
  override val JOffsetDateTimeDecoder: R2dbcDecoder<OffsetDateTime> =
    R2dbcDecoderAny(OffsetDateTime::class) { ctx, i -> ctx.row.get(i, OffsetDateTime::class.java) }


  /** java.util.Date -> bind as Instant (supported type)
   * original behavior is to assume the field actually supports timestamp with timezone
   */
  open override val JDateEncoder: R2dbcEncoder<Date> =
    R2dbcEncoderAny(NA, Date::class) { ctx, v, i -> ctx.stmt.bind(i, Instant.ofEpochMilli(v.getTime())) }
  /** java.util.Date from Instant
   * original behavior is to assume the field actually supports timestamp with timezone
   */
  open override val JDateDecoder: R2dbcDecoder<Date> =
    R2dbcDecoderAny(Date::class) { ctx, i -> ctx.row.get(i, Instant::class.java)?.let { Date.from(it) } }
}

object R2dbcUuidEncodingNative: JavaUuidEncoding<Connection, Statement, Row> {
  private const val NA = 0

  override val JUuidEncoder: R2dbcEncoder<UUID> =
    R2dbcEncoderAny(NA, UUID::class) { ctx, v, i -> ctx.stmt.bind(i, v) }

  override val JUuidDecoder: R2dbcDecoder<UUID> =
    R2dbcDecoderAny(UUID::class) { ctx, i -> ctx.row.get(i, UUID::class.java) }
}

object R2dbcUuidEncodingString: JavaUuidEncoding<Connection, Statement, Row> {
  private const val NA = 0

  override val JUuidEncoder: R2dbcEncoder<UUID> =
    R2dbcBasicEncoding.StringEncoder.contramap { v: UUID ->
      v.toString()
    }

  override val JUuidDecoder: R2dbcDecoder<UUID> =
    R2dbcBasicEncoding.StringDecoder.map { s: String ->
      UUID.fromString(s)
    }
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
  val encoders: Set<R2dbcEncoder<out Any>> = setOf(
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
    R2dbcUuidEncodingNative.JUuidEncoder,

    R2dbcAdditionalEncoding.BigDecimalEncoder
  )
}
