package io.exoquery.controller.delight

import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlPreparedStatement
import co.touchlab.sqliter.Cursor
import co.touchlab.sqliter.Statement
import io.exoquery.controller.sqlite.SqliteCursorWrapper
import io.exoquery.controller.sqlite.SqliteStatementWrapper

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

interface DelightCursorWrapper: SqliteCursorWrapper {
  companion object {
    operator fun invoke(cursor: Cursor): CursorWrapper = CursorWrapper(cursor)
    fun fromDelightCursor(cursor: SqlCursor): DelightCursorWrapper =
      object: DelightCursorWrapper {
        override fun getString(index: Int) = cursor.getString(index)
        override fun getLong(index: Int) = cursor.getLong(index)
        override fun getBytes(index: Int) = cursor.getBytes(index)
        override fun getDouble(index: Int) = cursor.getDouble(index)
        override fun isNull(index: Int) = cursor.getBytes(index) == null
      }
  }
}

data class CursorWrapper(val cursor: Cursor): DelightCursorWrapper {
  fun next() = cursor.next()
  override fun getString(index: Int) = cursor.getString(index)
  override fun getLong(index: Int) = cursor.getLong(index)
  override fun getBytes(index: Int) = cursor.getBytes(index)
  override fun getDouble(index: Int) = cursor.getDouble(index)
  override fun isNull(index: Int) = cursor.isNull(index)
}
