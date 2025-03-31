package io.exoquery.sql.delight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.exoquery.sql.*
import io.exoquery.sql.native.NativeEncodingConfig
import io.exoquery.sql.sqlite.SqliteCursorWrapper
import io.exoquery.sql.sqlite.SqliteSqlEncoding
import io.exoquery.sql.sqlite.SqliteStatementWrapper
import io.exoquery.sql.sqlite.Unused

class SqlDelightContext(
  val database: NativeSqliteDriver,
  override val encodingConfig: EncodingConfig<Unused, SqliteStatementWrapper, SqliteCursorWrapper> = NativeEncodingConfig()
): WithEncoding<Unused, SqliteStatementWrapper, SqliteCursorWrapper> {
  // SqlDelight does not expose the Sqliter cursor directly so there is no way to get column names or types
  override fun extractColumnInfo(row: SqliteCursorWrapper): List<ColumnInfo>? = null

  override val encodingApi: SqlEncoding<Unused, SqliteStatementWrapper, SqliteCursorWrapper> = SqliteSqlEncoding

  override val allEncoders by lazy { encodingApi.computeEncoders() + encodingConfig.additionalEncoders }
  override val allDecoders by lazy { encodingApi.computeDecoders() + encodingConfig.additionalDecoders }

  fun runToResult(query: Action, sqlDelightId: Int?): QueryResult<Long> =
    database.execute(
      sqlDelightId,
      query.sql,
      query.params.size,
      { prepare(DelightStatementWrapper.fromDelightStatement(this), Unused, query.params) }
    )

  fun <T> runToResult(query: Query<T>, sqlDelightId: Int?): QueryResult<List<T>> =
    database.executeQuery(
      sqlDelightId,
      query.sql,
      { cursor -> cursor.awaitAll { cursor -> query.resultMaker.makeExtractor(null).invoke(Unused, DelightCursorWrapper.fromDelightCursor(cursor)) } },
      query.params.size,
      { prepare(DelightStatementWrapper.fromDelightStatement(this), Unused, query.params) }
    )
}
