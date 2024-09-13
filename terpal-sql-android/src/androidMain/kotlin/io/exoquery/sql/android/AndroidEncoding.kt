package io.exoquery.sql.android

import android.database.AbstractWindowedCursor
import android.database.Cursor
import android.database.CursorWindow
import android.os.Build
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import androidx.sqlite.db.SupportSQLiteStatement
import io.exoquery.sql.EncodingConfig
import io.exoquery.sql.SqlDecoder
import io.exoquery.sql.SqlEncoder
import io.exoquery.sql.android.Api28Impl.setWindowSize
import io.exoquery.sql.sqlite.SqliteCursorWrapper
import io.exoquery.sql.sqlite.SqliteStatementWrapper
import io.exoquery.sql.sqlite.Unused
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

data class AndroidEncodingConfig private constructor(
  override val additionalEncoders: Set<SqlEncoder<Unused, SqliteStatementWrapper, out Any>>,
  override val additionalDecoders: Set<SqlDecoder<Unused, SqliteCursorWrapper, out Any>>,
  override val json: Json,
  // If you want to use any primitive-wrapped contextual encoders you need to add them here
  override val module: SerializersModule,
  override val timezone: TimeZone
): EncodingConfig<Unused, SqliteStatementWrapper, SqliteCursorWrapper> {
  companion object {
//    val Default get() =
//      Default(
//        AdditionalAndroidEncoding.encoders,
//        AdditionalAndroidEncoding.decoders
//      )
//
//    fun Default(
//      additionalEncoders: Set<SqlEncoder<Connection, SupportSQLiteStatement, out Any>> = setOf(),
//      additionalDecoders: Set<SqlDecoder<Connection, Cursor, out Any>> = setOf(),
//      json: Json = Json,
//      module: SerializersModule = EmptySerializersModule(),
//      timezone: TimeZone = TimeZone.currentSystemDefault()
//    ) = JdbcEncodingConfig(
//      additionalEncoders + AdditionalAndroidEncoding.encoders,
//      additionalDecoders + AdditionalAndroidEncoding.decoders,
//      json,
//      module,
//      timezone
//    )
//
//    operator fun invoke(
//      additionalEncoders: Set<SqlEncoder<Connection, SupportSQLiteStatement, out Any>> = setOf(),
//      additionalDecoders: Set<SqlDecoder<Connection, Cursor, out Any>> = setOf(),
//      json: Json = Json,
//      module: SerializersModule = EmptySerializersModule(),
//      timezone: TimeZone = TimeZone.currentSystemDefault()
//    ) = Default(additionalEncoders, additionalDecoders, json, module, timezone)

    fun Empty(
      additionalEncoders: Set<SqlEncoder<Unused, SqliteStatementWrapper, out Any>> = setOf(),
      additionalDecoders: Set<SqlDecoder<Unused, SqliteCursorWrapper, out Any>> = setOf(),
      json: Json = Json,
      module: SerializersModule = EmptySerializersModule(),
      timezone: TimeZone = TimeZone.currentSystemDefault()
    ) = AndroidEncodingConfig(additionalEncoders, additionalDecoders, json, module, timezone)
  }
}

@RequiresApi(Build.VERSION_CODES.P)
private object Api28Impl {
  @JvmStatic
  @DoNotInline
  fun AbstractWindowedCursor.setWindowSize(windowSizeBytes: Long) {
    window = CursorWindow(null, windowSizeBytes)
  }
}

class AndroidxCursorWrapper(val cursor: Cursor, windowSizeBytes: Long?): SqliteCursorWrapper {
  init {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
      windowSizeBytes != null &&
      cursor is AbstractWindowedCursor
    ) {
      cursor.setWindowSize(windowSizeBytes)
    }
  }

  fun next(): Boolean = cursor.moveToNext()
  override fun getString(index: Int): String = cursor.getString(index)
  override fun getLong(index: Int): Long = cursor.getLong(index)
  override fun getBytes(index: Int): ByteArray = cursor.getBlob(index)
  override fun getDouble(index: Int): Double = cursor.getDouble(index)
  override fun isNull(index: Int): Boolean = cursor.isNull(index)
}

// TODO implement java time encoders and java additional encoders for these since android JVM has them
@JvmInline
value class AndroidxStatementWrapper(val stmt: SupportSQLiteStatement): SqliteStatementWrapper {
  override fun bindBytes(index: Int, bytes: ByteArray) = if (bytes == null) stmt.bindNull(index) else stmt.bindBlob(index, bytes)
  override fun bindLong(index: Int, long: Long) = if (long == null) stmt.bindNull(index) else stmt.bindLong(index, long)
  override fun bindDouble(index: Int, double: Double) = if (double == null) stmt.bindNull(index) else stmt.bindDouble(index, double)
  override fun bindString(index: Int, string: String) = if (string == null) stmt.bindNull(index) else stmt.bindString(index, string)
  override fun bindNull(index: Int) = stmt.bindNull(index)
}

data class AndroidxArrayWrapper(val paramsSize: Int): SqliteStatementWrapper {
  val array: Array<Any?> = Array(paramsSize) { null }
  override fun bindBytes(index: Int, bytes: ByteArray) { array[index] = bytes }
  override fun bindLong(index: Int, long: Long) { array[index] = long }
  override fun bindDouble(index: Int, double: Double) { array[index] = double }
  override fun bindString(index: Int, string: String) { array[index] = string }
  override fun bindBoolean(index: Int, boolean: Boolean) { array[index] = boolean }
  override fun bindNull(index: Int) { array[index] = null }
}