package io.exoquery.controller.sqlite

import io.exoquery.controller.*
import kotlinx.datetime.*
import kotlin.reflect.KClass

object Unused

interface SqliteStatementWrapper {
  fun bindBytes(index: Int, bytes: ByteArray)
  fun bindLong(index: Int, long: Long)
  fun bindDouble(index: Int, double: Double)
  fun bindString(index: Int, string: String)
  fun bindBoolean(index: Int, boolean: Boolean) = bindLong(index, if (boolean) 1 else 0)
  fun bindNull(index: Int)
}

/**
 * Generic implementation for Sqlite Result Sets. I would have ideally liked
 * to have non-null guarentee but since this ultimately delegates to java methods
 * it is very hard to guarentee that here in a way that gives some kind of reasonable
 * error if the value is actually null coming out of the underlying Java ResultSet.
 * Instead I have an extra check in DecoderAny.decode since that is the
 * only place where all the debug information for a reasonable error (e.g. the column name)
 * is acctually available.
 */
interface SqliteCursorWrapper {
  fun getString(index: Int): String?
  fun getLong(index: Int): Long?
  fun getBytes(index: Int): ByteArray?
  fun getDouble(index: Int): Double?
  fun getBoolean(index: Int): Boolean? = if (!isNull(index)) getLong(index) == 1L else false
  fun isNull(index: Int): Boolean
}

typealias SqliteEncoder<T> = SqlEncoder<Unused, SqliteStatementWrapper, T>
typealias SqliteEncodingContext = EncodingContext<Unused, SqliteStatementWrapper>
typealias SqliteDecodingContext = DecodingContext<Unused, SqliteCursorWrapper>

typealias SqliteEncoderAny<T> = EncoderAny<T, SqliteFieldType, Unused, SqliteStatementWrapper>
typealias SqliteDecoderAny<T> = DecoderAny<T, Unused, SqliteCursorWrapper>

fun <T: Any> SqliteEncoderAny(dataType: SqliteFieldType, type: KClass<T>, f: (SqliteEncodingContext, T, Int) -> Unit) =
  EncoderAny<T, SqliteFieldType, Unused, SqliteStatementWrapper>(dataType, type, { index, stmt, _ -> stmt.bindNull(index) }, f)
fun <T: Any> SqliteDecoderAny(type: KClass<T>, f: (SqliteDecodingContext, Int) -> T?) =
  DecoderAny<T, Unused, SqliteCursorWrapper>(type, { index, cursor -> cursor.isNull(index) }, f)


object SqliteSqlEncoding: SqlEncoding<Unused, SqliteStatementWrapper, SqliteCursorWrapper>,
  BasicEncoding<Unused, SqliteStatementWrapper, SqliteCursorWrapper> by SqliteBasicEncoding,
  TimeEncoding<Unused, SqliteStatementWrapper, SqliteCursorWrapper> by SqliteTimeEncoding

object SqliteSqlEncodingStringTimes: SqlEncoding<Unused, SqliteStatementWrapper, SqliteCursorWrapper>,
  BasicEncoding<Unused, SqliteStatementWrapper, SqliteCursorWrapper> by SqliteBasicEncoding,
  TimeEncoding<Unused, SqliteStatementWrapper, SqliteCursorWrapper> by SqliteTimeStringEncoding


/**
 * The SQLite spec describes 5 kinds of fields but there isn't even
 * a consistent integer-code that each implementor decides to use so I
 * needed to encode all three sitautions.
 */
sealed interface SqliteFieldType {
  data object TYPE_INTEGER: SqliteFieldType
  data object TYPE_FLOAT: SqliteFieldType
  data object TYPE_TEXT: SqliteFieldType
  data object TYPE_BLOB: SqliteFieldType
  data object TYPE_NULL: SqliteFieldType

  /**
   * From the Sqliter Driver Driver:
   * @see co.touchlab.sqliter.Cursor.kt
   * public static final int SQLITE_INTEGER = 1;
   * public static final int SQLITE_FLOAT = 2;
   * public static final int SQLITE_TEXT = 3;
   * public static final int SQLITE_BLOB = 4;
   * public static final int SQLITE_NULL = 5;
   */
  companion object {


    fun fromSqliterCode(code: Int): SqliteFieldType =
      when {
        code == 1 -> SqliteFieldType.TYPE_INTEGER
        code == 2 -> SqliteFieldType.TYPE_FLOAT
        code == 3 -> SqliteFieldType.TYPE_TEXT
        code == 4 -> SqliteFieldType.TYPE_BLOB
        code == 5 -> SqliteFieldType.TYPE_NULL
        else -> throw IllegalArgumentException("Unsupported SQlite field type: $code")
      }

    /**
     * From the JDBC Sqlite Driver
     * @see org.sqlite.core.Codes.java
     *
     * public static final int SQLITE_INTEGER = 1;
     * public static final int SQLITE_FLOAT = 2;
     * public static final int SQLITE_TEXT = 3;
     * public static final int SQLITE_BLOB = 4;
     * public static final int SQLITE_NULL = 5;
     *
     */
    fun fromJdbcCode(code: Int): SqliteFieldType =
      when {
        code == 1 -> SqliteFieldType.TYPE_INTEGER
        code == 2 -> SqliteFieldType.TYPE_FLOAT
        code == 3 -> SqliteFieldType.TYPE_TEXT
        code == 4 -> SqliteFieldType.TYPE_BLOB
        code == 5 -> SqliteFieldType.TYPE_NULL
        else -> throw IllegalArgumentException("Unsupported SQlite field type: $code")
      }

    /**
     * From the Android Sqlite Driver
     * @see android.database.Cursor.java
     */
    fun fromAndroidCode(code: Int): SqliteFieldType =
      when {
        code == 0 -> SqliteFieldType.TYPE_NULL
        code == 1 -> SqliteFieldType.TYPE_INTEGER
        code == 2 -> SqliteFieldType.TYPE_FLOAT
        code == 3 -> SqliteFieldType.TYPE_TEXT
        code == 4 -> SqliteFieldType.TYPE_BLOB
        else -> throw IllegalArgumentException("Unsupported SQlite field type: $code")
      }
  }
}

object SqliteBasicEncoding: BasicEncoding<Unused, SqliteStatementWrapper, SqliteCursorWrapper> {
  override val StringEncoder: SqliteEncoderAny<String> = SqliteEncoderAny(SqliteFieldType.TYPE_TEXT, String::class) { ctx, value, index -> ctx.stmt.bindString(index, value) }
  override val StringDecoder: SqliteDecoderAny<String> = SqliteDecoderAny(String::class) { ctx, index -> ctx.row.getString(index) }

  override val BooleanEncoder: SqliteEncoderAny<Boolean> = SqliteEncoderAny(SqliteFieldType.TYPE_INTEGER, Boolean::class) { ctx, value, index -> ctx.stmt.bindLong(index, if (value) 1 else 0) }
  override val BooleanDecoder: SqliteDecoderAny<Boolean> = SqliteDecoderAny(Boolean::class) { ctx, index -> ctx.row.getLong(index) == 1L }

  override val IntEncoder: SqliteEncoderAny<Int> = SqliteEncoderAny(SqliteFieldType.TYPE_INTEGER, Int::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toLong()) }
  override val IntDecoder: SqliteDecoderAny<Int> = SqliteDecoderAny(Int::class) { ctx, index -> ctx.row.getLong(index)?.toInt() }

  override val ShortEncoder: SqliteEncoderAny<Short> = SqliteEncoderAny(SqliteFieldType.TYPE_INTEGER, Short::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toLong()) }
  override val ShortDecoder: SqliteDecoderAny<Short> = SqliteDecoderAny(Short::class) { ctx, index -> ctx.row.getLong(index)?.toShort() }

  override val LongEncoder: SqliteEncoderAny<Long> = SqliteEncoderAny(SqliteFieldType.TYPE_INTEGER, Long::class) { ctx, value, index -> ctx.stmt.bindLong(index, value) }
  override val LongDecoder: SqliteDecoderAny<Long> = SqliteDecoderAny(Long::class) { ctx, index -> ctx.row.getLong(index) }

  override val DoubleEncoder: SqliteEncoderAny<Double> = SqliteEncoderAny(SqliteFieldType.TYPE_FLOAT, Double::class) { ctx, value, index -> ctx.stmt.bindDouble(index, value) }
  override val DoubleDecoder: SqliteDecoderAny<Double> = SqliteDecoderAny(Double::class) { ctx, index -> ctx.row.getDouble(index) }

  override val ByteEncoder: SqliteEncoderAny<Byte> = SqliteEncoderAny(SqliteFieldType.TYPE_INTEGER, Byte::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toLong()) }
  override val ByteDecoder: SqliteDecoderAny<Byte> = SqliteDecoderAny(Byte::class) { ctx, index -> ctx.row.getLong(index)?.toByte() }

  override val CharEncoder: SqliteEncoderAny<Char> = SqliteEncoderAny(SqliteFieldType.TYPE_TEXT, Char::class) { ctx, value, index -> ctx.stmt.bindString(index, value.toString()) }
  override val CharDecoder: SqliteDecoderAny<Char> = SqliteDecoderAny(Char::class) { ctx, index -> ctx.row.getString(index)?.single() }

  override val FloatEncoder: SqliteEncoderAny<Float> = SqliteEncoderAny(SqliteFieldType.TYPE_FLOAT, Float::class) { ctx, value, index -> ctx.stmt.bindDouble(index, value.toDouble()) }
  override val FloatDecoder: SqliteDecoderAny<Float> = SqliteDecoderAny(Float::class) { ctx, index -> ctx.row.getDouble(index)?.toFloat() }

  override val ByteArrayEncoder: SqliteEncoderAny<ByteArray> = SqliteEncoderAny(SqliteFieldType.TYPE_BLOB, ByteArray::class) { ctx, value, index -> ctx.stmt.bindBytes(index, value) }
  override val ByteArrayDecoder: SqliteDecoderAny<ByteArray> = SqliteDecoderAny(ByteArray::class) { ctx, index -> ctx.row.getBytes(index) }

  override fun preview(index: Int, row: SqliteCursorWrapper): String? = row.getString(index)
  override fun isNull(index: Int, row: SqliteCursorWrapper): Boolean = row.isNull(index)
}

// TODO refactor to configureable date resolution the same way as  AdditionalAndroidEncoding works
object SqliteTimeEncoding: TimeEncoding<Unused, SqliteStatementWrapper, SqliteCursorWrapper> {
  override val InstantEncoder: SqliteEncoderAny<Instant> = SqliteEncoderAny(SqliteFieldType.TYPE_INTEGER, Instant::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toEpochMilliseconds()) }
  override val InstantDecoder: SqliteDecoderAny<Instant> = SqliteDecoderAny(Instant::class) { ctx, index -> ctx.row.getLong(index)?.let { Instant.fromEpochMilliseconds(it) } }

  override val LocalDateEncoder: SqliteEncoderAny<LocalDate> = SqliteEncoderAny(SqliteFieldType.TYPE_INTEGER, LocalDate::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toEpochDays().toLong()) }
  override val LocalDateDecoder: SqliteDecoderAny<LocalDate> = SqliteDecoderAny(LocalDate::class) { ctx, index -> ctx.row.getLong(index)?.let { LocalDate.fromEpochDays(it.toInt()) } }

  override val LocalTimeEncoder: SqliteEncoderAny<LocalTime> = SqliteEncoderAny(SqliteFieldType.TYPE_INTEGER, LocalTime::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toNanosecondOfDay()) }
  override val LocalTimeDecoder: SqliteDecoderAny<LocalTime> = SqliteDecoderAny(LocalTime::class) { ctx, index -> ctx.row.getLong(index)?.let { LocalTime.fromNanosecondOfDay(it) } }

  override val LocalDateTimeEncoder: SqliteEncoderAny<LocalDateTime> = SqliteEncoderAny(SqliteFieldType.TYPE_INTEGER, LocalDateTime::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toInstant(ctx.timeZone).toEpochMilliseconds()) }
  override val LocalDateTimeDecoder: SqliteDecoderAny<LocalDateTime> =
    SqliteDecoderAny(LocalDateTime::class) { ctx, index -> ctx.row.getLong(index)?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(ctx.timeZone) } }
}

object SqliteTimeStringEncoding: TimeEncoding<Unused, SqliteStatementWrapper, SqliteCursorWrapper> {
  override val InstantEncoder: SqliteEncoderAny<Instant> = SqliteEncoderAny(SqliteFieldType.TYPE_TEXT, Instant::class) { ctx, value, index -> ctx.stmt.bindString(index, value.toString()) }
  override val InstantDecoder: SqliteDecoderAny<Instant> = SqliteDecoderAny(Instant::class) { ctx, index -> ctx.row.getString(index)?.let { Instant.parse(it) } }

  override val LocalDateEncoder: SqliteEncoderAny<LocalDate> = SqliteEncoderAny(SqliteFieldType.TYPE_TEXT, LocalDate::class) { ctx, value, index -> ctx.stmt.bindString(index, value.toString()) }
  override val LocalDateDecoder: SqliteDecoderAny<LocalDate> = SqliteDecoderAny(LocalDate::class) { ctx, index -> ctx.row.getString(index)?.let { LocalDate.parse(it) } }

  override val LocalTimeEncoder: SqliteEncoderAny<LocalTime> = SqliteEncoderAny(SqliteFieldType.TYPE_TEXT, LocalTime::class) { ctx, value, index -> ctx.stmt.bindString(index, value.toString()) }
  override val LocalTimeDecoder: SqliteDecoderAny<LocalTime> = SqliteDecoderAny(LocalTime::class) { ctx, index -> ctx.row.getString(index)?.let { LocalTime.parse(it) } }

  override val LocalDateTimeEncoder: SqliteEncoderAny<LocalDateTime> = SqliteEncoderAny(SqliteFieldType.TYPE_TEXT, LocalDateTime::class) { ctx, value, index -> ctx.stmt.bindString(index, value.toString()) }
  override val LocalDateTimeDecoder: SqliteDecoderAny<LocalDateTime> = SqliteDecoderAny(LocalDateTime::class) { ctx, index -> ctx.row.getString(index)?.let { LocalDateTime.parse(it) } }
}
