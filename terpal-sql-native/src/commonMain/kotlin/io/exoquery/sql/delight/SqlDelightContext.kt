package io.exoquery.sql.delight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.FieldType
import io.exoquery.sql.*
import kotlinx.datetime.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlin.reflect.KClass

object Unused

class SqlDelightContext(val database: NativeSqliteDriver) {
  protected open val additionalEncoders: Set<SqlEncoder<Unused, SqlPreparedStatement, out Any>> = setOf()
  protected open val additionalDecoders: Set<SqlDecoder<Unused, SqlCursor, out Any>> = setOf()
  protected open val timezone: TimeZone = TimeZone.currentSystemDefault()

  // If you want to use any primitive-wrapped contextual encoders you need to add them here
  protected open val module: SerializersModule = EmptySerializersModule()

  protected val encodingApi: SqlEncoding<Unused, SqlPreparedStatement, SqlCursor> = DelightSqlEncoding

  protected val allEncoders by lazy { encodingApi.computeEncoders() + additionalEncoders }
  protected val allDecoders by lazy { encodingApi.computeDecoders() + additionalDecoders }
  protected val json: Json = Json

  protected open fun createEncodingContext(session: Unused, stmt: SqlPreparedStatement) = EncodingContext(session, stmt, timezone)
  protected open fun createDecodingContext(session: Unused, row: SqlCursor) = DecodingContext(session, row, timezone, null) // No metadata for for NativeSqliteDriver because I need to get it from touchlab Cursor which is not exposed

  @Suppress("UNCHECKED_CAST")
  fun <T> Param<T>.write(index: Int, conn: Unused, ps: SqlPreparedStatement): Unit {
    // TODO logging integration
    //println("----- Preparing parameter $index - $value - using $serializer")
    PreparedStatementElementEncoder(createEncodingContext(conn, ps), index /*+1*/, encodingApi, allEncoders, module, json).encodeNullableSerializableValue(serializer, value)
  }

  protected open fun prepare(stmt: SqlPreparedStatement, params: List<Param<*>>) =
    params.withIndex().forEach { (idx, param) ->
      param.write(idx, Unused, stmt)
    }

  protected fun <T> KSerializer<T>.makeExtractor() =
    { rs: SqlCursor ->
      val decoder = DelightRowDecoder(createDecodingContext(Unused, rs), module, encodingApi, allDecoders, descriptor, json)
      // If this is specifically a top-level class annotated with @SqlJsonValue it needs special decoding
      if (this.descriptor.isJsonClassAnnotated()) {
        decoder.decodeJsonAnnotated(descriptor, 0, this) ?:
          throw Exception("Error decoding json annotated class of the type: ${this.descriptor}")
      } else {
        deserialize(decoder)
      }
    }

  fun <T> run(query: Query<T>): QueryResult<List<T>> =
    database.executeQuery(
      null, // TODO see how to optimize caching, e.g. can we use the query hash as the identifier?
      query.sql,
      { cursor ->
        val first = cursor.next()
        val result = mutableListOf<T>()
        val mapper = query.resultMaker.makeExtractor()

        // If the cursor isn't async, we want to preserve the blocking semantics and execute it synchronously
        when (first) {
          is QueryResult.AsyncValue -> {
            QueryResult.AsyncValue {
              if (first.await()) result.add(mapper(cursor)) else return@AsyncValue result
              while (cursor.next().await()) result.add(mapper(cursor))
              result
            }
          }

          is QueryResult.Value -> {
            if (first.value) result.add(mapper(cursor)) else return@executeQuery QueryResult.Value(result)
            while (cursor.next().value) result.add(mapper(cursor))
            QueryResult.Value(result.toList())
          }
        }

      },
      query.params.size,
      { prepare(this, query.params) }
    )
}

//typealias NativeEncoder<T> = SqlEncoder<Unused, Statement, T>
//typealias NativeEncodingContext = EncodingContext<Unused, Statement>
//typealias NativeDecodingContext = DecodingContext<Unused, Cursor>
//typealias FieldType = Int

typealias DelightEncoder<T> = SqlEncoder<Unused, SqlPreparedStatement, T>
typealias DelightEncodingContext = EncodingContext<Unused, SqlPreparedStatement>
typealias DelightDecodingContext = DecodingContext<Unused, SqlCursor>

//class NativeEncoderAny<T: Any>(
//  override val dataType: FieldType,
//  override val type: KClass<T>,
//  override val f: (NativeEncodingContext, T, Int) -> Unit
//): EncoderAny<T, FieldType, Unused, Statement>(
//  dataType,
//  type,
//  { index, stmt, dataType -> stmt.bindNull(index) },
//  f
//)

class DelightEncoderAny<T: Any>(
  override val dataType: FieldType,
  override val type: KClass<T>,
  override val f: (DelightEncodingContext, T, Int) -> Unit
): EncoderAny<T, FieldType, Unused, SqlPreparedStatement>(
  dataType,
  type,
  { index, stmt, _ -> stmt.bindBytes(index, null) },
  f
)

//class NativeDecoderAny<T: Any>(
//  override val type: KClass<T>,
//  override val f: (NativeDecodingContext, Int) -> T
//): DecoderAny<T, Unused, Cursor>(
//  type,
//  { index, cursor -> cursor.isNull(index) },
//  f
//)

class DelightDecoderAny<T: Any>(
  override val type: KClass<T>,
  override val f: (DelightDecodingContext, Int) -> T
): DecoderAny<T, Unused, SqlCursor>(
  type,
  // TODO what happens if we call this on a string, int, etc... and it's not null, will it fail?
  { index, cursor -> cursor.getBytes(index) == null },
  f
)

object DelightSqlEncoding: SqlEncoding<Unused, SqlPreparedStatement, SqlCursor>,
  BasicEncoding<Unused, SqlPreparedStatement, SqlCursor> by DelightBasicEncoding,
  TimeEncoding<Unused, SqlPreparedStatement, SqlCursor> by DelightTimeEncoding

object DelightSqlEncodingStringTimes: SqlEncoding<Unused, SqlPreparedStatement, SqlCursor>,
  BasicEncoding<Unused, SqlPreparedStatement, SqlCursor> by DelightBasicEncoding,
  TimeEncoding<Unused, SqlPreparedStatement, SqlCursor> by DelightTimeStringEncoding

fun failNull(): Nothing = throw IllegalStateException("null value")

object DelightBasicEncoding: BasicEncoding<Unused, SqlPreparedStatement, SqlCursor> {
  override val StringEncoder: DelightEncoderAny<String> = DelightEncoderAny(FieldType.TYPE_TEXT, String::class) { ctx, value, index -> ctx.stmt.bindString(index, value) }
  override val StringDecoder: DelightDecoderAny<String> = DelightDecoderAny(String::class) { ctx, index -> ctx.row.getString(index) ?: failNull() }

  override val BooleanEncoder: DelightEncoderAny<Boolean> = DelightEncoderAny(FieldType.TYPE_INTEGER, Boolean::class) { ctx, value, index -> ctx.stmt.bindLong(index, if (value) 1 else 0) }
  override val BooleanDecoder: DelightDecoderAny<Boolean> = DelightDecoderAny(Boolean::class) { ctx, index -> ctx.row.getLong(index) == 1L }

  override val IntEncoder: DelightEncoderAny<Int> = DelightEncoderAny(FieldType.TYPE_INTEGER, Int::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toLong()) }
  override val IntDecoder: DelightDecoderAny<Int> = DelightDecoderAny(Int::class) { ctx, index -> ctx.row.getLong(index)?.toInt() ?: failNull() }

  override val ShortEncoder: DelightEncoderAny<Short> = DelightEncoderAny(FieldType.TYPE_INTEGER, Short::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toLong()) }
  override val ShortDecoder: DelightDecoderAny<Short> = DelightDecoderAny(Short::class) { ctx, index -> ctx.row.getLong(index)?.toShort() ?: failNull() }

  override val LongEncoder: DelightEncoderAny<Long> = DelightEncoderAny(FieldType.TYPE_INTEGER, Long::class) { ctx, value, index -> ctx.stmt.bindLong(index, value) }
  override val LongDecoder: DelightDecoderAny<Long> = DelightDecoderAny(Long::class) { ctx, index -> ctx.row.getLong(index) ?: failNull() }

  override val DoubleEncoder: DelightEncoderAny<Double> = DelightEncoderAny(FieldType.TYPE_FLOAT, Double::class) { ctx, value, index -> ctx.stmt.bindDouble(index, value) }
  override val DoubleDecoder: DelightDecoderAny<Double> = DelightDecoderAny(Double::class) { ctx, index -> ctx.row.getDouble(index) ?: failNull() }

  override val ByteEncoder: DelightEncoderAny<Byte> = DelightEncoderAny(FieldType.TYPE_INTEGER, Byte::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toLong()) }
  override val ByteDecoder: DelightDecoderAny<Byte> = DelightDecoderAny(Byte::class) { ctx, index -> ctx.row.getLong(index)?.toByte() ?: failNull() }

  override val CharEncoder: DelightEncoderAny<Char> = DelightEncoderAny(FieldType.TYPE_TEXT, Char::class) { ctx, value, index -> ctx.stmt.bindString(index, value.toString()) }
  override val CharDecoder: DelightDecoderAny<Char> = DelightDecoderAny(Char::class) { ctx, index -> ctx.row.getString(index)?.single() ?: failNull() }

  override val FloatEncoder: DelightEncoderAny<Float> = DelightEncoderAny(FieldType.TYPE_FLOAT, Float::class) { ctx, value, index -> ctx.stmt.bindDouble(index, value.toDouble()) }
  override val FloatDecoder: DelightDecoderAny<Float> = DelightDecoderAny(Float::class) { ctx, index -> ctx.row.getDouble(index)?.toFloat() ?: failNull() }

  override val ByteArrayEncoder: DelightEncoderAny<ByteArray> = DelightEncoderAny(FieldType.TYPE_BLOB, ByteArray::class) { ctx, value, index -> ctx.stmt.bindBytes(index, value) }
  override val ByteArrayDecoder: DelightDecoderAny<ByteArray> = DelightDecoderAny(ByteArray::class) { ctx, index -> ctx.row.getBytes(index) ?: failNull() }

  override fun preview(index: Int, row: SqlCursor): String? = row.getString(index)
  override fun isNull(index: Int, row: SqlCursor): Boolean = row.getBytes(index) == null
}

object DelightTimeEncoding: TimeEncoding<Unused, SqlPreparedStatement, SqlCursor> {
  override val InstantEncoder: DelightEncoderAny<Instant> = DelightEncoderAny(FieldType.TYPE_INTEGER, Instant::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toEpochMilliseconds()) }
  override val InstantDecoder: DelightDecoderAny<Instant> = DelightDecoderAny(Instant::class) { ctx, index -> ctx.row.getLong(index)?.let { Instant.fromEpochMilliseconds(it) } ?: failNull() }

  override val LocalDateEncoder: DelightEncoderAny<LocalDate> = DelightEncoderAny(FieldType.TYPE_INTEGER, LocalDate::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toEpochDays().toLong()) }
  override val LocalDateDecoder: DelightDecoderAny<LocalDate> = DelightDecoderAny(LocalDate::class) { ctx, index -> ctx.row.getLong(index)?.let { LocalDate.fromEpochDays(it.toInt()) } ?: failNull() }

  override val LocalTimeEncoder: DelightEncoderAny<LocalTime> = DelightEncoderAny(FieldType.TYPE_INTEGER, LocalTime::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toNanosecondOfDay()) }
  override val LocalTimeDecoder: DelightDecoderAny<LocalTime> = DelightDecoderAny(LocalTime::class) { ctx, index -> ctx.row.getLong(index)?.let { LocalTime.fromNanosecondOfDay(it) } ?: failNull() }

  override val LocalDateTimeEncoder: DelightEncoderAny<LocalDateTime> = DelightEncoderAny(FieldType.TYPE_INTEGER, LocalDateTime::class) { ctx, value, index -> ctx.stmt.bindLong(index, value.toInstant(ctx.timeZone).toEpochMilliseconds()) }
  override val LocalDateTimeDecoder: DelightDecoderAny<LocalDateTime> = DelightDecoderAny(LocalDateTime::class) { ctx, index -> ctx.row.getLong(index)?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(ctx.timeZone) } ?: failNull() }
}

object DelightTimeStringEncoding: TimeEncoding<Unused, SqlPreparedStatement, SqlCursor> {
  override val InstantEncoder: DelightEncoderAny<Instant> = DelightEncoderAny(FieldType.TYPE_TEXT, Instant::class) { ctx, value, index -> ctx.stmt.bindString(index, value.toString()) }
  override val InstantDecoder: DelightDecoderAny<Instant> = DelightDecoderAny(Instant::class) { ctx, index -> ctx.row.getString(index)?.let { Instant.parse(it) } ?: failNull() }

  override val LocalDateEncoder: DelightEncoderAny<LocalDate> = DelightEncoderAny(FieldType.TYPE_TEXT, LocalDate::class) { ctx, value, index -> ctx.stmt.bindString(index, value.toString()) }
  override val LocalDateDecoder: DelightDecoderAny<LocalDate> = DelightDecoderAny(LocalDate::class) { ctx, index -> ctx.row.getString(index)?.let { LocalDate.parse(it) } ?: failNull() }

  override val LocalTimeEncoder: DelightEncoderAny<LocalTime> = DelightEncoderAny(FieldType.TYPE_TEXT, LocalTime::class) { ctx, value, index -> ctx.stmt.bindString(index, value.toString()) }
  override val LocalTimeDecoder: DelightDecoderAny<LocalTime> = DelightDecoderAny(LocalTime::class) { ctx, index -> ctx.row.getString(index)?.let { LocalTime.parse(it) } ?: failNull() }

  override val LocalDateTimeEncoder: DelightEncoderAny<LocalDateTime> = DelightEncoderAny(FieldType.TYPE_TEXT, LocalDateTime::class) { ctx, value, index -> ctx.stmt.bindString(index, value.toString()) }
  override val LocalDateTimeDecoder: DelightDecoderAny<LocalDateTime> = DelightDecoderAny(LocalDateTime::class) { ctx, index -> ctx.row.getString(index)?.let { LocalDateTime.parse(it) } ?: failNull() }
}


class DelightRowDecoder(
  ctx: DelightDecodingContext,
  module: SerializersModule,
  initialRowIndex: Int,
  api: ApiDecoders<Unused, SqlCursor>,
  decoders: Set<SqlDecoder<Unused, SqlCursor, out Any>>,
  type: RowDecoderType,
  json: Json,
  endCallback: (Int) -> Unit
): RowDecoder<Unused, SqlCursor>(ctx, module, initialRowIndex, api, decoders, type, json, endCallback) {
  companion object {
    operator fun invoke(
      ctx: DelightDecodingContext,
      module: SerializersModule,
      api: ApiDecoders<Unused, SqlCursor>,
      decoders: Set<SqlDecoder<Unused, SqlCursor, out Any>>,
      descriptor: SerialDescriptor,
      json: Json
    ): DelightRowDecoder {
      return DelightRowDecoder(ctx, module, 0, api, decoders, RowDecoderType.Regular, json, {})
    }
  }

  override fun cloneSelf(ctx: DelightDecodingContext, initialRowIndex: Int, type: RowDecoderType, endCallback: (Int) -> Unit): RowDecoder<Unused, SqlCursor> =
    DelightRowDecoder(ctx, this.serializersModule, initialRowIndex, api, decoders, type, json, endCallback)
}

/*
class JdbcRowDecoder(
  ctx: JdbcDecodingContext,
  module: SerializersModule,
  initialRowIndex: Int,
  api: ApiDecoders<Connection, ResultSet>,
  decoders: Set<SqlDecoder<Connection, ResultSet, out Any>>,
  columnInfos: List<ColumnInfo>,
  type: RowDecoderType,
  json: Json,
  endCallback: (Int) -> Unit
): RowDecoder<Connection, ResultSet>(ctx, module, initialRowIndex, api, decoders, columnInfos, type, json, endCallback) {

  companion object {
    operator fun invoke(
      ctx: JdbcDecodingContext,
      module: SerializersModule,
      api: ApiDecoders<Connection, ResultSet>,
      decoders: Set<SqlDecoder<Connection, ResultSet, out Any>>,
      descriptor: SerialDescriptor,
      json: Json
    ): JdbcRowDecoder {
      fun metaColumnData(meta: ResultSetMetaData) =
        (1..meta.columnCount).map { ColumnInfo(meta.getColumnName(it), meta.getColumnTypeName(it)) }
      val metaColumns = metaColumnData(ctx.row.metaData)
      descriptor.verifyColumns(metaColumns)
      return JdbcRowDecoder(ctx, module, 1, api, decoders, metaColumns, RowDecoderType.Regular, json, {})
    }
  }

  override fun cloneSelf(ctx: JdbcDecodingContext, initialRowIndex: Int, type: RowDecoderType, endCallback: (Int) -> Unit): RowDecoder<Connection, ResultSet> =
    JdbcRowDecoder(ctx, this.serializersModule, initialRowIndex, api, decoders, columnInfos, type, json, endCallback)
}

 */