package io.exoquery.sql.jdbc

import io.exoquery.sql.*
import kotlinx.datetime.*
import java.math.BigDecimal
import java.sql.*
import java.time.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.*
import java.util.TimeZone

/** Represents a Jdbc encoder with a nullable or non-nulalble input value */
typealias JdbcEncoder<T> = SqlEncoder<Connection, PreparedStatement, T>

fun kotlinx.datetime.TimeZone.toJava(): TimeZone = TimeZone.getTimeZone(this.toJavaZoneId())

/** Represents a Jdbc Encoder with a non-nullable input value */
abstract class JdbcEncoderAny<T: Any>: JdbcEncoder<T>() {
  abstract val jdbcType: Int

  override fun asNullable(): JdbcEncoder<T?> =
    object: JdbcEncoder<T?>() {
      override val type = this@JdbcEncoderAny.type
      val jdbcType = this@JdbcEncoderAny.jdbcType
      override fun asNullable(): SqlEncoder<Connection, PreparedStatement, T?> = this

      override fun encode(ctx: JdbcEncodingContext, value: T?, index: Int) =
        try {
          if (value != null)
            this@JdbcEncoderAny.encode(ctx, value, index)
          else
            ctx.stmt.setNull(index, jdbcType)
        } catch (e: Throwable) {
          throw EncodingException("Error encoding ${type} value: $value at index: $index (whose jdbc-type: ${jdbcType})", e)
        }
    }

  inline fun <reified R: Any> contramap(crossinline f: (R) -> T):JdbcEncoderAny<R> =
    object: JdbcEncoderAny<R>() {
      override val type = R::class
      // Get the JDBC type from the parent. This makes sense because most of the time contramapped encoders are from primivites
      // e.g. StringDecoder.contramap { ... } so we want the jdbc type from the parent.
      override val jdbcType = this@JdbcEncoderAny.jdbcType
      override fun encode(ctx: JdbcEncodingContext, value: R, index: Int) =
        this@JdbcEncoderAny.encode(ctx, f(value), index)
    }

  /*
  expected:<[EncodingTestEntity(v1=s, v2=1.1, v3=true, v4=11, v5=23, v6=33, v7=431, v8=34.4, v9=42.0, v10=[1, 2], v11=Fri Nov 22 19:00:00 EST 2013, v12=EncodingTestType(value=s), v13=2013-11-23, v14=173fb134-c84b-4bb2-be5c-85b2552f60b5, o1=s, o2=1.1, o3=true, o4=11, o5=23, o6=33, o7=431, o8=34.4, o9=42.0, o10=[1, 2], o11=Fri Nov 22 19:00:00 EST 2013, o12=EncodingTestType(value=s), o13=2013-11-23, o14=348e85a2-d953-4cb6-a2ff-a90f02006eb4),
             EncodingTestEntity(v1=, v2=0, v3=false, v4=0, v5=0, v6=0, v7=0, v8=0.0, v9=0.0, v10=[], v11=1969-12-31, v12=EncodingTestType(value=), v13=1970-01-01, v14=00000000-0000-0000-0000-000000000000, o1=null, o2=null, o3=null, o4=null, o5=null, o6=null, o7=null, o8=null, o9=null, o10=null, o11=null, o12=null, o13=null, o14=null)]> but was:<[EncodingTestEntity(v1=s, v2=1.10, v3=true, v4=11, v5=23, v6=33, v7=431, v8=34.4, v9=42.0, v10=[1, 2], v11=Fri Nov 22 19:00:00 EST 2013, v12=EncodingTestType(value=s), v13=2013-11-23, v14=173fb134-c84b-4bb2-be5c-85b2552f60b5, o1=s, o2=1.10, o3=true, o4=null, o5=null, o6=null, o7=null, o8=null, o9=null, o10=null, o11=null, o12=null, o13=null, o14=null), EncodingTestEntity(v1=, v2=0.00, v3=false, v4=0, v5=0, v6=0, v7=0, v8=0.0, v9=0.0, v10=[], v11=Wed Dec 31 19:00:00 EST 1969, v12=EncodingTestType(value=), v13=1970-01-01, v14=00000000-0000-0000-0000-000000000000, o1=null, o2=null, o3=null, o4=null, o5=null, o6=null, o7=null, o8=null, o9=null, o10=null, o11=null, o12=null, o13=null, o14=null)]>
   */

  companion object {
    inline fun <reified T: Any> fromFunction(jdbcTypeNum: Int, crossinline f: (JdbcEncodingContext, T, Int) -> Unit): JdbcEncoderAny<T> =
      object: JdbcEncoderAny<T>() {
        override val jdbcType: Int = jdbcTypeNum
        override val type = T::class
        override fun encode(ctx: JdbcEncodingContext, value: T, index: Int) =
          f(ctx, value, index)
      }
  }
}

open class JdbcEncodingBasic:
  BasicEncoding<Connection, PreparedStatement, ResultSet>,
  JavaBigDecimalEncoding<Connection, PreparedStatement, ResultSet>,
  JavaLegacyDateEncoding<Connection, PreparedStatement, ResultSet> {
  companion object: JdbcEncodingBasic()

  override val ByteEncoder: JdbcEncoderAny<Byte> = JdbcEncoderAny.fromFunction(Types.TINYINT) { ctx, v, i -> ctx.stmt.setByte(i, v) }
  override val CharEncoder: JdbcEncoderAny<Char> = JdbcEncoderAny.fromFunction(Types.VARCHAR) { ctx, v, i -> ctx.stmt.setString(i, v.toString()) }
  override val DoubleEncoder: JdbcEncoderAny<Double> = JdbcEncoderAny.fromFunction(Types.DOUBLE) { ctx, v, i -> ctx.stmt.setDouble(i, v) }
  override val FloatEncoder: JdbcEncoderAny<Float> = JdbcEncoderAny.fromFunction(Types.FLOAT) { ctx, v, i -> ctx.stmt.setFloat(i, v) }
  override val IntEncoder: JdbcEncoderAny<Int> = JdbcEncoderAny.fromFunction(Types.INTEGER) { ctx, v, i -> ctx.stmt.setInt(i, v) }
  override val LongEncoder: JdbcEncoderAny<Long> = JdbcEncoderAny.fromFunction(Types.BIGINT) { ctx, v, i -> ctx.stmt.setLong(i, v) }
  override val ShortEncoder: JdbcEncoderAny<Short> = JdbcEncoderAny.fromFunction(Types.SMALLINT) { ctx, v, i -> ctx.stmt.setShort(i, v) }
  override val StringEncoder: JdbcEncoderAny<String> = JdbcEncoderAny.fromFunction(Types.VARCHAR) { ctx, v, i -> ctx.stmt.setString(i, v) }
  override val BigDecimalEncoder: JdbcEncoderAny<BigDecimal> = JdbcEncoderAny.fromFunction(Types.NUMERIC) { ctx, v, i -> ctx.stmt.setBigDecimal(i, v) }
  override val ByteArrayEncoder: JdbcEncoderAny<ByteArray> = JdbcEncoderAny.fromFunction(Types.VARBINARY) { ctx, v, i -> ctx.stmt.setBytes(i, v) }
  override val DateEncoder: JdbcEncoderAny<java.util.Date> =
    JdbcEncoderAny.fromFunction(Types.TIMESTAMP) { ctx, v, i ->
      ctx.stmt.setTimestamp(i, java.sql.Timestamp(v.getTime()), Calendar.getInstance(TimeZone.getTimeZone(ctx.timeZone.toJavaZoneId())))
    }

  override fun preview(index: Int, row: ResultSet): String? = row.getObject(index)?.let { it.toString() }
  override fun isNull(index: Int, row: ResultSet): Boolean {
    row.getObject(index)
    return row.wasNull()
  }
  override val ByteDecoder: JdbcDecoderAny<Byte> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getByte(i) }
  override val CharDecoder: JdbcDecoderAny<Char> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getString(i)[0] }
  override val DoubleDecoder: JdbcDecoderAny<Double> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getDouble(i) }
  override val FloatDecoder: JdbcDecoderAny<Float> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getFloat(i) }
  override val IntDecoder: JdbcDecoderAny<Int> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getInt(i) }
  override val LongDecoder: JdbcDecoderAny<Long> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getLong(i) }
  override val ShortDecoder: JdbcDecoderAny<Short> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getShort(i) }
  override val StringDecoder: JdbcDecoderAny<String> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getString(i) }
  override val BigDecimalDecoder: JdbcDecoderAny<BigDecimal> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getBigDecimal(i) }
  override val ByteArrayDecoder: JdbcDecoderAny<ByteArray> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getBytes(i) }
  override val DateDecoder: JdbcDecoderAny<java.util.Date> =
    JdbcDecoderAny.fromFunction { ctx, i ->
      java.util.Date(ctx.row.getTimestamp(i, Calendar.getInstance(ctx.timeZone.toJava())).getTime())
    }
}

object AdditionalPostgresEncoding {
  val SqlJsonEncoder: JdbcEncoder<SqlJson> = JdbcEncoderAny.fromFunction(Types.OTHER) { ctx, v, i -> ctx.stmt.setObject(i, v.value, Types.OTHER) }
  val SqlJsonDecoder: JdbcDecoder<SqlJson> = JdbcDecoderAny.fromFunction { ctx, i -> SqlJson(ctx.row.getString(i)) }

  val encoders = setOf(SqlJsonEncoder)
  val decoders = setOf(SqlJsonDecoder)
}

object AdditionaJdbcTimeEncoding {
  val SqlDateEncoder: JdbcEncoderAny<java.sql.Date> = JdbcEncoderAny.fromFunction(Types.DATE) { ctx, v, i -> ctx.stmt.setDate(i, v) }
  val SqlTimeEncoder: JdbcEncoderAny<java.sql.Time> = JdbcEncoderAny.fromFunction(Types.TIME) { ctx, v, i -> ctx.stmt.setTime(i, v) }
  val SqlTimestampEncoder: JdbcEncoderAny<Timestamp> = JdbcEncoderAny.fromFunction(Types.TIMESTAMP) { ctx, v, i -> ctx.stmt.setTimestamp(i, v) }
  val SqlDateDecoder: JdbcDecoderAny<java.sql.Date> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getDate(i) }
  val SqlTimeDecoder: JdbcDecoderAny<java.sql.Time> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getTime(i) }
  val SqlTimestampDecoder: JdbcDecoderAny<java.sql.Timestamp> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getTimestamp(i) }

  val encoders = setOf(SqlDateEncoder, SqlTimeEncoder, SqlTimestampEncoder)
  val decoders = setOf(SqlDateDecoder, SqlTimeDecoder, SqlTimestampDecoder)
}

open class JdbcTimeEncoding: JavaTimeEncoding<Connection, PreparedStatement, ResultSet> {
  // Encoders
  open val jdbcTypeOfLocalDate     = Types.DATE
  open val jdbcTypeOfLocalTime     = Types.TIME
  open val jdbcTypeOfLocalDateTime = Types.TIMESTAMP
  open val jdbcTypeOfZonedDateTime = Types.TIMESTAMP_WITH_TIMEZONE
  open val jdbcTypeOfInstant                      = Types.TIMESTAMP_WITH_TIMEZONE
  open val jdbcTypeOfOffsetTime                   = Types.TIME_WITH_TIMEZONE
  open val jdbcTypeOfOffsetDateTime               = Types.TIMESTAMP_WITH_TIMEZONE
  open val timezone: TimeZone = TimeZone.getDefault()
  open fun jdbcEncodeInstant(value: Instant): Any = value.atOffset(ZoneOffset.UTC)

  // Encoders for the KMP datetimes
  override val LocalDateEncoder: JdbcEncoderAny<kotlinx.datetime.LocalDate> = JdbcEncoderAny.fromFunction(jdbcTypeOfLocalDate) { ctx, v, i -> ctx.stmt.setObject(i, v.toJavaLocalDate(), jdbcTypeOfLocalDate) }
  override val LocalDateTimeEncoder: JdbcEncoderAny<kotlinx.datetime.LocalDateTime> = JdbcEncoderAny.fromFunction(jdbcTypeOfLocalDateTime) { ctx, v, i -> ctx.stmt.setObject(i, v.toJavaLocalDateTime(), jdbcTypeOfLocalDateTime) }
  override val LocalTimeEncoder: JdbcEncoderAny<kotlinx.datetime.LocalTime> = JdbcEncoderAny.fromFunction(jdbcTypeOfLocalTime) { ctx, v, i -> ctx.stmt.setObject(i, v.toJavaLocalTime(), jdbcTypeOfLocalTime) }
  override val InstantEncoder: JdbcEncoderAny<kotlinx.datetime.Instant> = JdbcEncoderAny.fromFunction(jdbcTypeOfInstant) { ctx, v, i -> ctx.stmt.setObject(i, jdbcEncodeInstant(v.toJavaInstant()), jdbcTypeOfInstant) }

  override val JLocalDateEncoder: JdbcEncoderAny<LocalDate> = JdbcEncoderAny.fromFunction(jdbcTypeOfLocalDate) { ctx, v, i -> ctx.stmt.setObject(i, v, jdbcTypeOfLocalDate) }
  override val JLocalTimeEncoder: JdbcEncoderAny<LocalTime> = JdbcEncoderAny.fromFunction(jdbcTypeOfLocalTime) { ctx, v, i -> ctx.stmt.setObject(i, v, jdbcTypeOfLocalTime) }
  override val JLocalDateTimeEncoder: JdbcEncoderAny<LocalDateTime> = JdbcEncoderAny.fromFunction(jdbcTypeOfLocalDateTime) { ctx, v, i -> ctx.stmt.setObject(i, v, jdbcTypeOfLocalDateTime) }
  override val JZonedDateTimeEncoder: JdbcEncoderAny<ZonedDateTime> = JdbcEncoderAny.fromFunction(jdbcTypeOfZonedDateTime) { ctx, v, i -> ctx.stmt.setObject(i, v.toOffsetDateTime(), jdbcTypeOfZonedDateTime) }

  override val JInstantEncoder: JdbcEncoderAny<Instant> = JdbcEncoderAny.fromFunction(jdbcTypeOfInstant) { ctx, v, i -> ctx.stmt.setObject(i, jdbcEncodeInstant(v), jdbcTypeOfInstant) }
  override val JOffsetTimeEncoder: JdbcEncoderAny<OffsetTime> = JdbcEncoderAny.fromFunction(jdbcTypeOfOffsetTime) { ctx, v, i -> ctx.stmt.setObject(i, v, jdbcTypeOfOffsetTime) }
  override val JOffsetDateTimeEncoder: JdbcEncoderAny<OffsetDateTime> = JdbcEncoderAny.fromFunction(jdbcTypeOfOffsetDateTime) { ctx, v, i -> ctx.stmt.setObject(i, v, jdbcTypeOfOffsetDateTime) }

  // Decoders for the KMP datetimes
  override val LocalDateDecoder: JdbcDecoderAny<kotlinx.datetime.LocalDate> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getObject(i, LocalDate::class.java).toKotlinLocalDate() }
  override val LocalDateTimeDecoder: JdbcDecoderAny<kotlinx.datetime.LocalDateTime> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getObject(i, LocalDateTime::class.java).toKotlinLocalDateTime() }
  override val LocalTimeDecoder: JdbcDecoderAny<kotlinx.datetime.LocalTime> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getObject(i, LocalTime::class.java).toKotlinLocalTime() }
  override val InstantDecoder: JdbcDecoderAny<kotlinx.datetime.Instant> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getObject(i, Instant::class.java).toKotlinInstant() }

  // Decoders
  override val JLocalDateDecoder: JdbcDecoderAny<LocalDate> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getObject(i, LocalDate::class.java) }
  override val JLocalTimeDecoder: JdbcDecoderAny<LocalTime> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getObject(i, LocalTime::class.java) }
  override val JLocalDateTimeDecoder: JdbcDecoderAny<LocalDateTime> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getObject(i, LocalDateTime::class.java) }
  override val JZonedDateTimeDecoder: JdbcDecoderAny<ZonedDateTime> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getObject(i, OffsetDateTime::class.java).toZonedDateTime() }

  override val JInstantDecoder: JdbcDecoderAny<Instant> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getObject(i, OffsetDateTime::class.java).toInstant() }
  override val JOffsetTimeDecoder: JdbcDecoderAny<OffsetTime> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getObject(i, OffsetTime::class.java) }
  override val JOffsetDateTimeDecoder: JdbcDecoderAny<OffsetDateTime> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getObject(i, OffsetDateTime::class.java) }
}

object JdbcTimeEncodingLegacy: JavaTimeEncoding<Connection, PreparedStatement, ResultSet> {
  // KMP Date Encoders
  override val LocalDateEncoder: JdbcEncoderAny<kotlinx.datetime.LocalDate> = JdbcEncoderAny.fromFunction(Types.DATE) { ctx, v, i -> ctx.stmt.setDate(i, java.sql.Date.valueOf(v.toJavaLocalDate())) }
  override val LocalDateTimeEncoder: JdbcEncoderAny<kotlinx.datetime.LocalDateTime> = JdbcEncoderAny.fromFunction(Types.TIMESTAMP) { ctx, v, i -> ctx.stmt.setTimestamp(i, java.sql.Timestamp.valueOf(v.toJavaLocalDateTime())) }
  override val LocalTimeEncoder: JdbcEncoderAny<kotlinx.datetime.LocalTime> = JdbcEncoderAny.fromFunction(Types.TIME) { ctx, v, i -> ctx.stmt.setTime(i, java.sql.Time.valueOf(v.toJavaLocalTime())) }
  override val InstantEncoder: JdbcEncoderAny<kotlinx.datetime.Instant> = JdbcEncoderAny.fromFunction(Types.TIMESTAMP) { ctx, v, i -> ctx.stmt.setTimestamp(i, java.sql.Timestamp.from(v.toJavaInstant())) }

  override val JLocalDateEncoder: JdbcEncoderAny<LocalDate> = JdbcEncoderAny.fromFunction(Types.DATE) { ctx, v, i -> ctx.stmt.setDate(i, java.sql.Date.valueOf(v)) }
  override val JLocalTimeEncoder: JdbcEncoderAny<LocalTime> = JdbcEncoderAny.fromFunction(Types.TIME) { ctx, v, i -> ctx.stmt.setTime(i, java.sql.Time.valueOf(v)) }
  override val JLocalDateTimeEncoder: JdbcEncoderAny<LocalDateTime> = JdbcEncoderAny.fromFunction(Types.TIMESTAMP) { ctx, v, i -> ctx.stmt.setTimestamp(i, Timestamp.valueOf(v)) }
  override val JZonedDateTimeEncoder: JdbcEncoderAny<ZonedDateTime> = JdbcEncoderAny.fromFunction(Types.TIMESTAMP) { ctx, v, i -> ctx.stmt.setTimestamp(i, Timestamp.from(v.toInstant())) }
  override val JInstantEncoder: JdbcEncoderAny<Instant> = JdbcEncoderAny.fromFunction(Types.TIMESTAMP) { ctx, v, i -> ctx.stmt.setTimestamp(i, Timestamp.from(v)) }
  override val JOffsetTimeEncoder: JdbcEncoderAny<OffsetTime> = JdbcEncoderAny.fromFunction(Types.TIME) { ctx, v, i -> ctx.stmt.setTime(i, java.sql.Time.valueOf(v.withOffsetSameInstant(ZoneOffset.UTC).toLocalTime()))  }
  override val JOffsetDateTimeEncoder: JdbcEncoderAny<OffsetDateTime> = JdbcEncoderAny.fromFunction(Types.TIMESTAMP) { ctx, v, i -> ctx.stmt.setTimestamp(i, java.sql.Timestamp.from(v.toInstant())) }

  // KMP Date Decoders
  override val LocalDateDecoder: JdbcDecoderAny<kotlinx.datetime.LocalDate> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getDate(i).toLocalDate().toKotlinLocalDate() }
  override val LocalDateTimeDecoder: JdbcDecoderAny<kotlinx.datetime.LocalDateTime> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getTimestamp(i).toLocalDateTime().toKotlinLocalDateTime() }
  override val LocalTimeDecoder: JdbcDecoderAny<kotlinx.datetime.LocalTime> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getTime(i).toLocalTime().toKotlinLocalTime() }
  override val InstantDecoder: JdbcDecoderAny<kotlinx.datetime.Instant> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getTimestamp(i).toInstant().toKotlinInstant() }

  override val JLocalDateDecoder: JdbcDecoderAny<LocalDate> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getDate(i).toLocalDate() }
  override val JLocalTimeDecoder: JdbcDecoderAny<LocalTime> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getTime(i).toLocalTime() }
  override val JLocalDateTimeDecoder: JdbcDecoderAny<LocalDateTime> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getTimestamp(i).toLocalDateTime() }
  override val JZonedDateTimeDecoder: JdbcDecoderAny<ZonedDateTime> = JdbcDecoderAny.fromFunction { ctx, i -> ZonedDateTime.ofInstant(ctx.row.getTimestamp(i).toInstant(), ctx.timeZone.toJavaZoneId()) }
  override val JInstantDecoder: JdbcDecoderAny<Instant> = JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getTimestamp(i).toInstant() }
  override val JOffsetTimeDecoder: JdbcDecoderAny<OffsetTime> = JdbcDecoderAny.fromFunction { ctx, i -> OffsetTime.of(ctx.row.getTime(i).toLocalTime(), ZoneOffset.UTC) }
  override val JOffsetDateTimeDecoder: JdbcDecoderAny<OffsetDateTime> = JdbcDecoderAny.fromFunction { ctx, i -> OffsetDateTime.ofInstant(ctx.row.getTimestamp(i).toInstant(), ctx.timeZone.toJavaZoneId()) }
}
