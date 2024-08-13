package io.exoquery.sql.native

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.Cursor
import co.touchlab.sqliter.DatabaseConnection
import co.touchlab.sqliter.Statement
import co.touchlab.sqliter.FieldType
import io.exoquery.sql.*
import kotlinx.datetime.*
import kotlin.reflect.KClass

class SqlDelightContext(val driver: NativeSqliteDriver) {



  suspend fun <T> run(query: Query<T>): List<T> = TODO()
}

typealias NativeEncoder<T> = SqlEncoder<DatabaseConnection, Statement, T>
typealias NativeEncodingContext = EncodingContext<DatabaseConnection, Statement>
typealias NativeDecodingContext = DecodingContext<DatabaseConnection, Cursor>
typealias FieldType = Int

class NativeEncoderAny<T: Any>(
  override val dataType: FieldType,
  override val type: KClass<T>,
  override val f: (NativeEncodingContext, T, Int) -> Unit
): EncoderAny<T, FieldType, DatabaseConnection, Statement>(
  dataType,
  type,
  { index, stmt, dataType -> stmt.bindNull(index) },
  f
)

class NativeDecoderAny<T: Any>(
  override val type: KClass<T>,
  override val f: (NativeDecodingContext, Int) -> T
): DecoderAny<T, DatabaseConnection, Cursor>(
  type,
  { index, cursor -> cursor.isNull(index) },
  f
)

object NativeSqlEncoding: SqlEncoding<DatabaseConnection, Statement, Cursor>,
  BasicEncoding<DatabaseConnection, Statement, Cursor> by NativeBasicEncoding,
  TimeEncoding<DatabaseConnection, Statement, Cursor> by NativeTimeEncoding

object NativeBasicEncoding: BasicEncoding<DatabaseConnection, Statement, Cursor> {
  override val StringEncoder: NativeEncoderAny<String> = NativeEncoderAny(FieldType.TYPE_TEXT, String::class) { ctx, value, index -> ctx.stmt.bindString(index, value) }
  override val StringDecoder: NativeDecoderAny<String> = NativeDecoderAny(String::class) { ctx, index -> ctx.row.getString(index) }

  override val BooleanEncoder: NativeEncoderAny<Boolean> = NativeEncoderAny(FieldType.TYPE_INTEGER, Boolean::class) { ctx, value, index -> ctx.stmt.bindLong(index, if (value) 1 else 0) }
  override val BooleanDecoder: NativeDecoderAny<Boolean> = NativeDecoderAny(Boolean::class) { ctx, index -> ctx.row.getLong(index) == 1L }

  override val IntDecoder: NativeDecoderAny<Int> = NativeDecoderAny(Int::class) { ctx, index -> ctx.row.getLong(index).toInt() }
  override val IntEncoder: NativeEncoderAny<Int> = NativeEncoderAny(FieldType.TYPE_INTEGER, Int::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toLong()) }

  override val ShortEncoder: NativeEncoderAny<Short> = NativeEncoderAny(FieldType.TYPE_INTEGER, Short::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toLong()) }
  override val ShortDecoder: NativeDecoderAny<Short> = NativeDecoderAny(Short::class) { ctx, index -> ctx.row.getLong(index).toShort() }

  override val LongEncoder: NativeEncoderAny<Long> = NativeEncoderAny(FieldType.TYPE_INTEGER, Long::class) { ctx, value, index -> ctx.stmt.bindLong(index, value) }
  override val LongDecoder: NativeDecoderAny<Long> = NativeDecoderAny(Long::class) { ctx, index -> ctx.row.getLong(index) }

  override val DoubleEncoder: NativeEncoderAny<Double> = NativeEncoderAny(FieldType.TYPE_FLOAT, Double::class) { ctx, value, index -> ctx.stmt.bindDouble(index, value) }
  override val DoubleDecoder: NativeDecoderAny<Double> = NativeDecoderAny(Double::class) { ctx, index -> ctx.row.getDouble(index) }

  override val ByteEncoder: NativeEncoderAny<Byte> = NativeEncoderAny(FieldType.TYPE_INTEGER, Byte::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toLong()) }
  override val ByteDecoder: NativeDecoderAny<Byte> = NativeDecoderAny(Byte::class) { ctx, index -> ctx.row.getLong(index).toByte() }

  override val CharEncoder: NativeEncoderAny<Char> = NativeEncoderAny(FieldType.TYPE_TEXT, Char::class) { ctx, value, index -> ctx.stmt.bindString(index, value.toString()) }
  override val CharDecoder: NativeDecoderAny<Char> = NativeDecoderAny(Char::class) { ctx, index -> ctx.row.getString(index).single() }

  override val FloatEncoder: NativeEncoderAny<Float> = NativeEncoderAny(FieldType.TYPE_FLOAT, Float::class) { ctx, value, index -> ctx.stmt.bindDouble(index, value.toDouble()) }
  override val FloatDecoder: NativeDecoderAny<Float> = NativeDecoderAny(Float::class) { ctx, index -> ctx.row.getDouble(index).toFloat() }

  override val ByteArrayEncoder: NativeEncoderAny<ByteArray> = NativeEncoderAny(FieldType.TYPE_BLOB, ByteArray::class) { ctx, value, index -> ctx.stmt.bindBlob(index, value) }
  override val ByteArrayDecoder: NativeDecoderAny<ByteArray> = NativeDecoderAny(ByteArray::class) { ctx, index -> ctx.row.getBytes(index) }

  override fun preview(index: Int, row: Cursor): String? = row.getString(index)
  override fun isNull(index: Int, row: Cursor): Boolean = row.isNull(index)
}

object NativeTimeEncoding: TimeEncoding<DatabaseConnection, Statement, Cursor> {
  override val InstantEncoder: NativeEncoderAny<Instant> = NativeEncoderAny(FieldType.TYPE_INTEGER, Instant::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toEpochMilliseconds()) }
  override val InstantDecoder: NativeDecoderAny<Instant> = NativeDecoderAny(Instant::class) { ctx, index -> Instant.fromEpochMilliseconds(ctx.row.getLong(index)) }

  override val LocalDateEncoder: NativeEncoderAny<LocalDate> = NativeEncoderAny(FieldType.TYPE_INTEGER, LocalDate::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toEpochDays().toLong()) }
  override val LocalDateDecoder: NativeDecoderAny<LocalDate> = NativeDecoderAny(LocalDate::class) { ctx, index -> LocalDate.fromEpochDays(ctx.row.getLong(index).toInt()) }

  override val LocalTimeEncoder: NativeEncoderAny<LocalTime> = NativeEncoderAny(FieldType.TYPE_INTEGER, LocalTime::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toNanosecondOfDay()) }
  override val LocalTimeDecoder: NativeDecoderAny<LocalTime> = NativeDecoderAny(LocalTime::class) { ctx, index -> LocalTime.fromNanosecondOfDay(ctx.row.getLong(index)) }

  override val LocalDateTimeEncoder: NativeEncoderAny<LocalDateTime> = NativeEncoderAny(FieldType.TYPE_INTEGER, LocalDateTime::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toInstant(ctx.timeZone).toEpochMilliseconds()) }
  override val LocalDateTimeDecoder: NativeDecoderAny<LocalDateTime> = NativeDecoderAny(LocalDateTime::class) { ctx, index -> Instant.fromEpochMilliseconds(ctx.row.getLong(index)).toLocalDateTime(ctx.timeZone) }
}

object NativeTimeStringEncoding: TimeEncoding<DatabaseConnection, Statement, Cursor> {
  override val InstantEncoder: NativeEncoderAny<Instant> = NativeEncoderAny(FieldType.TYPE_TEXT, Instant::class) { ctx, value, index -> ctx.stmt.bindString(index, value.toString()) }
  override val InstantDecoder: NativeDecoderAny<Instant> = NativeDecoderAny(Instant::class) { ctx, index -> Instant.parse(ctx.row.getString(index)) }

  override val LocalDateEncoder: NativeEncoderAny<LocalDate> = NativeEncoderAny(FieldType.TYPE_TEXT, LocalDate::class) { ctx, value, index -> ctx.stmt.bindString(index, value.toString()) }
  override val LocalDateDecoder: NativeDecoderAny<LocalDate> = NativeDecoderAny(LocalDate::class) { ctx, index -> LocalDate.parse(ctx.row.getString(index)) }

  override val LocalTimeEncoder: NativeEncoderAny<LocalTime> = NativeEncoderAny(FieldType.TYPE_TEXT, LocalTime::class) { ctx, value, index -> ctx.stmt.bindString(index, value.toString()) }
  override val LocalTimeDecoder: NativeDecoderAny<LocalTime> = NativeDecoderAny(LocalTime::class) { ctx, index -> LocalTime.parse(ctx.row.getString(index)) }

  override val LocalDateTimeEncoder: NativeEncoderAny<LocalDateTime> = NativeEncoderAny(FieldType.TYPE_TEXT, LocalDateTime::class) { ctx, value, index -> ctx.stmt.bindString(index, value.toString()) }
  override val LocalDateTimeDecoder: NativeDecoderAny<LocalDateTime> = NativeDecoderAny(LocalDateTime::class) { ctx, index -> LocalDateTime.parse(ctx.row.getString(index)) }
}
