package io.exoquery.sql.delight

import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlPreparedStatement
import co.touchlab.sqliter.Cursor
import co.touchlab.sqliter.Statement
import io.exoquery.sql.sqlite.SqliteCursorWrapper
import io.exoquery.sql.sqlite.SqliteStatementWrapper
import io.exoquery.sql.DecodingContext
import io.exoquery.sql.sqlite.Unused

interface DelightStatementWrapper: SqliteStatementWrapper {
  companion object {
    operator fun invoke(stmt: Statement): StatementWrapper = StatementWrapper(stmt)
    fun fromDelightStatement(stmt: SqlPreparedStatement): DelightStatementWrapper =
      object: DelightStatementWrapper {
        override fun bindBytes(index: Int, bytes: ByteArray) = stmt.bindBytes(index, bytes)
        override fun bindLong(index: Int, long: Long) = stmt.bindLong(index, long)
        override fun bindDouble(index: Int, double: Double) = stmt.bindDouble(index, double)
        override fun bindString(index: Int, string: String) = stmt.bindString(index, string)
        override fun bindNull(index: Int) = stmt.bindBytes(index, null)
      }
  }
}

data class StatementWrapper(val stmt: Statement): DelightStatementWrapper {
  override fun bindBytes(index: Int, bytes: ByteArray) = stmt.bindBlob(index, bytes)
  override fun bindLong(index: Int, long: Long) = run {
    println("------------- Binding long $long at index: $index")
    stmt.bindLong(index, long)
  }
  override fun bindDouble(index: Int, double: Double) = stmt.bindDouble(index, double)
  override fun bindString(index: Int, string: String) = stmt.bindString(index, string)
  override fun bindNull(index: Int) = stmt.bindNull(index)
}

 fun failNull(index: Int): Nothing = run {
   throw IllegalStateException("Cannot read column $index in the query")
 }

interface DelightCursorWrapper: SqliteCursorWrapper {
  companion object {
    operator fun invoke(cursor: Cursor): CursorWrapper = CursorWrapper(cursor)
    fun fromDelightCursor(cursor: SqlCursor): DelightCursorWrapper =
      object: DelightCursorWrapper {
        override fun getString(index: Int): String = cursor.getString(index) ?: failNull(index)
        override fun getLong(index: Int): Long = cursor.getLong(index) ?: failNull(index)
        override fun getBytes(index: Int): ByteArray = cursor.getBytes(index) ?: failNull(index)
        override fun getDouble(index: Int): Double = cursor.getDouble(index) ?: failNull(index)
        override fun isNull(index: Int): Boolean = cursor.getBytes(index) == null
      }
  }
}

data class CursorWrapper(val cursor: Cursor): DelightCursorWrapper {
  fun next() = cursor.next()
  override fun getString(index: Int): String = cursor.getString(index)
  override fun getLong(index: Int): Long = cursor.getLong(index)
  override fun getBytes(index: Int): ByteArray = cursor.getBytes(index)
  override fun getDouble(index: Int): Double = cursor.getDouble(index)
  override fun isNull(index: Int): Boolean = cursor.isNull(index)
}


