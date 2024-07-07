package io.exoquery.sql.jdbc

import io.exoquery.sql.*
import kotlinx.serialization.descriptors.SerialDescriptor
import java.sql.Connection
import java.sql.ResultSet
import java.sql.ResultSetMetaData

class JdbcRowDecoder(
  ctx: JdbcDecodingContext,
  initialRowIndex: Int,
  api: ApiDecoders<Connection, ResultSet>,
  decoders: Set<SqlDecoder<Connection, ResultSet, out Any>>,
  columnInfos: List<ColumnInfo>,
  type: RowDecoderType,
  endCallback: (Int) -> Unit
): RowDecoder<Connection, ResultSet>(ctx, initialRowIndex, api, decoders, columnInfos, type, endCallback) {

  companion object {
    operator fun invoke(
      ctx: JdbcDecodingContext,
      api: ApiDecoders<Connection, ResultSet>,
      decoders: Set<SqlDecoder<Connection, ResultSet, out Any>>,
      descriptor: SerialDescriptor
    ): JdbcRowDecoder {
      fun metaColumnData(meta: ResultSetMetaData) =
        (1..meta.columnCount).map { ColumnInfo(meta.getColumnName(it), meta.getColumnTypeName(it)) }
      val metaColumns = metaColumnData(ctx.row.metaData)
      descriptor.verifyColumns(metaColumns)
      return JdbcRowDecoder(ctx, 1, api, decoders, metaColumns, RowDecoderType.Regular, {})
    }
  }

  override fun cloneSelf(ctx: JdbcDecodingContext, initialRowIndex: Int, type: RowDecoderType, endCallback: (Int) -> Unit): RowDecoder<Connection, ResultSet> =
    JdbcRowDecoder(ctx, initialRowIndex, api, decoders, columnInfos, type, endCallback)
}
