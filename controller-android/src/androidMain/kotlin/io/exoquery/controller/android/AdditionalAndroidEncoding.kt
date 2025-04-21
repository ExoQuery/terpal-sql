package io.exoquery.controller.android

import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import io.exoquery.controller.EncoderAny
import io.exoquery.controller.android.time.toSqlDate
import io.exoquery.controller.android.time.toSqlTime
import io.exoquery.controller.android.time.toSqlTimestamp
import io.exoquery.controller.sqlite.SqliteDecoderAny
import io.exoquery.controller.sqlite.SqliteDecodingContext
import io.exoquery.controller.sqlite.SqliteEncoderAny
import io.exoquery.controller.sqlite.SqliteFieldType
import io.exoquery.controller.sqlite.SqliteStatementWrapper
import java.math.BigDecimal
import java.sql.SQLException
import java.sql.Time
import java.sql.Timestamp
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Note that SQlite cursor wrappers use a 0-based index as opposed to a 1-based index so we can get columnInfos[i] instead of columnInfos[i-1]
 */
fun SqliteDecodingContext.getFieldType(column: Int): SqliteFieldType? {
  return columnInfos?.get(column)?.type?.let { SqliteFieldType.fromAndroidCode(it.toInt()) }
}

open class AdditionalSqliteEncoding(val dateHelper: SqliteDateHelper = SqliteDateHelper()) {

  val UuidEncoder: SqliteEncoderAny<UUID> = SqliteEncoderAny(SqliteFieldType.TYPE_TEXT, UUID::class) { ctx, v, i -> ctx.stmt.bindString(i, v.toString()) }
  val UuidDecoder: SqliteDecoderAny<UUID> = SqliteDecoderAny(UUID::class) { ctx, i ->
    val colType = ctx.getFieldType(i)
    if (colType == SqliteFieldType.TYPE_TEXT) {
      UUID.fromString(ctx.row.getString(i))
    } else {
      throw SQLException("Bad value for type UUID : ${ctx.row.getString(i)}")
    }
  }

  val BigDecimalEncoder: SqliteEncoderAny<BigDecimal> = SqliteEncoderAny(SqliteFieldType.TYPE_TEXT, BigDecimal::class) { ctx, v, i -> ctx.stmt.bindString(i, v.toPlainString()) }

  /**
    * In JDBC It works like this:
    * switch (safeGetColumnType(checkCol(col))) {
    *       case SQLITE_NULL:
    *           return null;
    *       case SQLITE_FLOAT:
    *           return BigDecimal.valueOf(safeGetDoubleCol(col));
    *       case SQLITE_INTEGER:
    *           return BigDecimal.valueOf(safeGetLongCol(col));
    *       default:
    *           final String stringValue = safeGetColumnText(col);
    *           try { return new BigDecimal(stringValue); } catch (NumberFormatException e) { throw new SQLException("Bad value for type BigDecimal : " + stringValue); }
    *   }
    *
    * It doesn't look like JDBC does anything with efficient byte encoders as a Sqlite TYPE_BLOB so I have also skilled this case.
    */
  val BigDecimalDecoder: SqliteDecoderAny<BigDecimal> = SqliteDecoderAny(BigDecimal::class) { ctx, i ->
    when (val colType = ctx.getFieldType(i)) {
      // Null encoding of value is handled by the .asNullable() method of DecoderAny
      //SqliteFieldType.TYPE_NULL -> null
      SqliteFieldType.TYPE_FLOAT -> BigDecimal.valueOf(ctx.row.getDouble(i))
      SqliteFieldType.TYPE_INTEGER -> BigDecimal.valueOf(ctx.row.getLong(i))
      else -> {
        val stringValue = ctx.row.getString(i)
        try {
          BigDecimal(stringValue)
        } catch (e: NumberFormatException) {
          throw SQLException("Bad value for type BigDecimal : $stringValue")
        }
      }
    }
  }

  val SqlDateEncoder: SqliteEncoderAny<java.sql.Date> =
    SqliteEncoderAny(SqliteFieldType.TYPE_INTEGER, java.sql.Date::class) { ctx, v, i -> dateHelper.setDateByMilliseconds(i, v.time, Calendar.getInstance(), ctx.stmt) }

  val SqlTimeEncoder: SqliteEncoderAny<Time> =
    SqliteEncoderAny(SqliteFieldType.TYPE_INTEGER, Time::class) { ctx, v, i -> dateHelper.setDateByMilliseconds(i, v.time, Calendar.getInstance(), ctx.stmt) }

  val SqlTimestampEncoder: SqliteEncoderAny<Timestamp> =
    SqliteEncoderAny(SqliteFieldType.TYPE_INTEGER, Timestamp::class) { ctx, v, i -> dateHelper.setDateByMilliseconds(i, v.time, Calendar.getInstance(), ctx.stmt) }

  val SqlDateDecoder: SqliteDecoderAny<java.sql.Date> = SqliteDecoderAny(java.sql.Date::class) { ctx, i ->  java.sql.Date(dateHelper.getTimeInMilliseconds(ctx, i)) }
  val SqlTimeDecoder: SqliteDecoderAny<Time> = SqliteDecoderAny(Time::class) { ctx, i -> java.sql.Time(dateHelper.getTimeInMilliseconds(ctx, i)) }
  val SqlTimestampDecoder: SqliteDecoderAny<Timestamp> = SqliteDecoderAny(Timestamp::class) { ctx, i -> java.sql.Timestamp(dateHelper.getTimeInMilliseconds(ctx, i)) }

  val JDateEncoder: SqliteEncoderAny<java.util.Date> = SqlTimestampEncoder.contramap { v: java.util.Date -> Timestamp(v.getTime()) }
  val JDateDecoder: SqliteDecoderAny<java.util.Date> = SqlTimestampDecoder.map { v: Timestamp -> java.util.Date(v.getTime()) }

  val JLocalDateEncoder: SqliteEncoderAny<java.time.LocalDate> = SqlDateEncoder.contramap { v: java.time.LocalDate -> v.toSqlDate(dateHelper.encodingZoneId) }
  val JLocalTimeEncoder: SqliteEncoderAny<java.time.LocalTime> = SqlTimeEncoder.contramap { v: java.time.LocalTime -> v.toSqlTime(dateHelper.encodingZoneId) }
  val JLocalDateTimeEncoder: SqliteEncoderAny<java.time.LocalDateTime> = SqlTimestampEncoder.contramap { v: java.time.LocalDateTime -> v.toSqlTimestamp(dateHelper.encodingZoneId) }
  val JZonedDateTimeEncoder: SqliteEncoderAny<java.time.ZonedDateTime> = SqlTimestampEncoder.contramap { v: java.time.ZonedDateTime -> Timestamp(v.toInstant().toEpochMilli()) }
  val JInstantEncoder: SqliteEncoderAny<java.time.Instant> = SqlTimestampEncoder.contramap { v: java.time.Instant -> Timestamp(v.toEpochMilli()) }
  val JOffsetTimeEncoder: SqliteEncoderAny<java.time.OffsetTime> = SqlTimeEncoder.contramap { v: java.time.OffsetTime -> v.toLocalTime().toSqlTime(dateHelper.encodingZoneId) }
  val JOffsetDateTimeEncoder: SqliteEncoderAny<java.time.OffsetDateTime> = SqlTimestampEncoder.contramap { v: java.time.OffsetDateTime -> Timestamp(v.toInstant().toEpochMilli()) }

  internal fun makeInstant(millis: Long): java.time.Instant {
    return java.time.Instant.ofEpochMilli(millis)
  }

  val JLocalDateDecoder: SqliteDecoderAny<java.time.LocalDate> = SqlDateDecoder.map { v: java.sql.Date -> makeInstant(v.time).atZone(dateHelper.encodingZoneId).toLocalDate() }
  val JLocalTimeDecoder: SqliteDecoderAny<java.time.LocalTime> = SqlTimeDecoder.map { v: java.sql.Time -> makeInstant(v.time).atZone(dateHelper.encodingZoneId).toLocalTime() }
  val JLocalDateTimeDecoder: SqliteDecoderAny<java.time.LocalDateTime> = SqlTimestampDecoder.map { v: java.sql.Timestamp -> makeInstant(v.time).atZone(dateHelper.encodingZoneId).toLocalDateTime() }
  val JZonedDateTimeDecoder: SqliteDecoderAny<java.time.ZonedDateTime> = SqlTimestampDecoder.map { v: java.sql.Timestamp -> makeInstant(v.time).atZone(dateHelper.encodingZoneId) }
  val JInstantDecoder: SqliteDecoderAny<java.time.Instant> = SqlTimestampDecoder.map { v: java.sql.Timestamp -> makeInstant(v.time) }
  val JOffsetTimeDecoder: SqliteDecoderAny<java.time.OffsetTime> =
    SqlTimeDecoder.map { v: java.sql.Time -> makeInstant(v.time).atZone(dateHelper.encodingZoneId).toOffsetDateTime().toOffsetTime() }
  val JOffsetDateTimeDecoder: SqliteDecoderAny<java.time.OffsetDateTime> =
    SqlTimestampDecoder.map { v: java.sql.Timestamp -> makeInstant(v.time).atZone(dateHelper.encodingZoneId).toOffsetDateTime() }



//  override val JDateEncoder: JdbcEncoderAny<java.util.Date> = JdbcEncoderAny(Types.TIMESTAMP, java.util.Date::class) { ctx, v, i ->
//    ctx.stmt.setTimestamp(i, Timestamp(v.getTime()), Calendar.getInstance(ctx.timeZone.toJava()))
//  }
//  override val JDateDecoder: JdbcDecoderAny<java.util.Date> = JdbcDecoderAny(java.util.Date::class) { ctx, i ->
//    java.util.Date(ctx.row.getTimestamp(i, Calendar.getInstance(ctx.timeZone.toJava())).getTime())
//  }
//  override val JLocalDateEncoder: JdbcEncoderAny<LocalDate> = JdbcEncoderAny(Types.DATE, LocalDate::class) { ctx, v, i -> ctx.stmt.setDate(i, java.sql.Date.valueOf(v)) }
//  override val JLocalTimeEncoder: JdbcEncoderAny<LocalTime> = JdbcEncoderAny(Types.TIME, LocalTime::class) { ctx, v, i -> ctx.stmt.setTime(i, Time.valueOf(v)) }
//  override val JLocalDateTimeEncoder: JdbcEncoderAny<LocalDateTime> = JdbcEncoderAny(Types.TIMESTAMP, LocalDateTime::class) { ctx, v, i -> ctx.stmt.setTimestamp(i, Timestamp.valueOf(v)) }
//  override val JZonedDateTimeEncoder: JdbcEncoderAny<ZonedDateTime> = JdbcEncoderAny(Types.TIMESTAMP, ZonedDateTime::class) { ctx, v, i -> ctx.stmt.setTimestamp(i, Timestamp.from(v.toInstant())) }
//  override val JInstantEncoder: JdbcEncoderAny<Instant> = JdbcEncoderAny(Types.TIMESTAMP, Instant::class) { ctx, v, i -> ctx.stmt.setTimestamp(i, Timestamp.from(v)) }
//  override val JOffsetTimeEncoder: JdbcEncoderAny<OffsetTime> = JdbcEncoderAny(Types.TIME, OffsetTime::class) { ctx, v, i -> ctx.stmt.setTime(i, Time.valueOf(v.withOffsetSameInstant(ZoneOffset.UTC).toLocalTime()))  }
//  override val JOffsetDateTimeEncoder: JdbcEncoderAny<OffsetDateTime> = JdbcEncoderAny(Types.TIMESTAMP, OffsetDateTime::class) { ctx, v, i -> ctx.stmt.setTimestamp(i, Timestamp.from(v.toInstant())) }
//
//  override val JLocalDateDecoder: JdbcDecoderAny<LocalDate> = JdbcDecoderAny(LocalDate::class) { ctx, i -> ctx.row.getDate(i).toLocalDate() }
//  override val JLocalTimeDecoder: JdbcDecoderAny<LocalTime> = JdbcDecoderAny(LocalTime::class) { ctx, i -> ctx.row.getTime(i).toLocalTime() }
//  override val JLocalDateTimeDecoder: JdbcDecoderAny<LocalDateTime> = JdbcDecoderAny(LocalDateTime::class) { ctx, i -> ctx.row.getTimestamp(i).toLocalDateTime() }
//  override val JZonedDateTimeDecoder: JdbcDecoderAny<ZonedDateTime> = JdbcDecoderAny(ZonedDateTime::class) { ctx, i -> ZonedDateTime.ofInstant(ctx.row.getTimestamp(i).toInstant(), ctx.timeZone.toJavaZoneId()) }
//  override val JInstantDecoder: JdbcDecoderAny<Instant> = JdbcDecoderAny(Instant::class) { ctx, i -> ctx.row.getTimestamp(i).toInstant() }
//  override val JOffsetTimeDecoder: JdbcDecoderAny<OffsetTime> = JdbcDecoderAny(OffsetTime::class) { ctx, i -> OffsetTime.of(ctx.row.getTime(i).toLocalTime(), ZoneOffset.UTC) }
//  override val JOffsetDateTimeDecoder: JdbcDecoderAny<OffsetDateTime> = JdbcDecoderAny(OffsetDateTime::class) { ctx, i -> OffsetDateTime.ofInstant(ctx.row.getTimestamp(i).toInstant(), ctx.timeZone.toJavaZoneId()) }

  val encoders = setOf(BigDecimalEncoder, SqlDateEncoder, SqlTimeEncoder, SqlTimestampEncoder,
    JDateEncoder, JLocalDateEncoder, JLocalTimeEncoder, JLocalDateTimeEncoder, JZonedDateTimeEncoder,
    JInstantEncoder, JOffsetTimeEncoder, JOffsetDateTimeEncoder, UuidEncoder)
  val decoders = setOf(BigDecimalDecoder, SqlDateDecoder, SqlTimeDecoder, SqlTimestampDecoder,
    JDateDecoder, JLocalDateDecoder, JLocalTimeDecoder, JLocalDateTimeDecoder, JZonedDateTimeDecoder,
    JInstantDecoder, JOffsetTimeDecoder, JOffsetDateTimeDecoder, UuidDecoder)
}


sealed interface SetDateBy {
  data object TEXT : SetDateBy
  data object REAL : SetDateBy
  data object INTEGER : SetDateBy
}

sealed interface DatePrecision {
  data object Millisecond: DatePrecision
  data object Second: DatePrecision
}

data class SqliteDateHelper(
  val setDateBy: SetDateBy = SetDateBy.INTEGER,
  val dateStringFormat: String = DEFAULT_DATE_STRING_FORMAT,
  val datePrecision: DatePrecision = DatePrecision.Millisecond,
  val encodingZoneId: ZoneId = ZoneId.systemDefault().normalized()
) {
  companion object {
    val DEFAULT_DATE_STRING_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
  }

  fun dateMultiplier() =
    when (datePrecision) {
      DatePrecision.Millisecond -> 1000L
      DatePrecision.Second -> 1L
    }

  fun getTimeInMilliseconds(ctx: SqliteDecodingContext, i: Int): Long {
    val colType = ctx.getFieldType(i)
    return when (colType) {
      SqliteFieldType.TYPE_TEXT -> {
        val dateText = ctx.row.getString(i)
        val date = SimpleDateFormat.getPatternInstance(this.dateStringFormat).parse(dateText)
        date.time
      }
      SqliteFieldType.TYPE_INTEGER -> ctx.row.getLong(i) * this.dateMultiplier()
      SqliteFieldType.TYPE_FLOAT -> {
        val convertedValue = julianDateToCalendar(ctx.row.getDouble(i), java.util.Calendar.getInstance())
          ?: throw SQLException("Bad value for type java.sql.Date : ${ctx.row.getString(i)}")
        convertedValue.timeInMillis
      }
      else -> throw SQLException("Bad value for type java.sql.Date : ${ctx.row.getString(i)}")
    }
  }

  /*
    protected void setDateByMilliseconds(int pos, Long value, Calendar calendar) throws SQLException {
        SQLiteConnectionConfig config = conn.getConnectionConfig();
        switch (config.getDateClass()) {
            case TEXT:
                batch(pos,FastDateFormat.getInstance(config.getDateStringFormat(), calendar.getTimeZone()).format(new Date(value)));
                break;
            case REAL:
                // long to Julian date
                batch(pos, new Double((value / 86400000.0) + 2440587.5));
                break;
            default: // INTEGER:
                batch(pos, new Long(value / config.getDateMultiplier()));
        }
    }
   */
  fun setDateByMilliseconds(pos: Int, value: Long, calendar: android.icu.util.Calendar, stmt: SqliteStatementWrapper) {
    when (setDateBy) {
      SetDateBy.TEXT ->
        stmt.bindString(pos, SimpleDateFormat.getPatternInstance(calendar, dateStringFormat, Locale.getDefault()).format(Date(value)))
      SetDateBy.REAL ->
        stmt.bindDouble(pos, (value / 86400000.0) + 2440587.5)
      SetDateBy.INTEGER ->
        stmt.bindLong(pos, value / dateMultiplier())
    }
  }
}




//interface JavaTimeEncoding<Session, Stmt, Row>: TimeEncoding<Session, Stmt, Row> {
//  val JDateEncoder: SqlEncoder<Session, Stmt, Date>
//  val JLocalDateEncoder: SqlEncoder<Session, Stmt, LocalDate>
//  val JLocalTimeEncoder: SqlEncoder<Session, Stmt, LocalTime>
//  val JLocalDateTimeEncoder: SqlEncoder<Session, Stmt, LocalDateTime>
//  val JZonedDateTimeEncoder: SqlEncoder<Session, Stmt, ZonedDateTime>
//  val JInstantEncoder: SqlEncoder<Session, Stmt, Instant>
//  val JOffsetTimeEncoder: SqlEncoder<Session, Stmt, OffsetTime>
//  val JOffsetDateTimeEncoder: SqlEncoder<Session, Stmt, OffsetDateTime>
//
//  val JDateDecoder: SqlDecoder<Session, Row, Date>
//  val JLocalDateDecoder: SqlDecoder<Session, Row, LocalDate>
//  val JLocalTimeDecoder: SqlDecoder<Session, Row, LocalTime>
//  val JLocalDateTimeDecoder: SqlDecoder<Session, Row, LocalDateTime>
//  val JZonedDateTimeDecoder: SqlDecoder<Session, Row, ZonedDateTime>
//  val JInstantDecoder: SqlDecoder<Session, Row, Instant>
//  val JOffsetTimeDecoder: SqlDecoder<Session, Row, OffsetTime>
//  val JOffsetDateTimeDecoder: SqlDecoder<Session, Row, OffsetDateTime>
//}
