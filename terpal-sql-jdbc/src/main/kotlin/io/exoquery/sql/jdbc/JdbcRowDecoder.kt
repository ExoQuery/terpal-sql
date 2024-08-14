package io.exoquery.sql.jdbc

import io.exoquery.sql.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.sql.Connection
import java.sql.ResultSet
import java.sql.ResultSetMetaData

// TODO no jdbc-specific functionality now, put this back into RowDecoder
class JdbcRowDecoder(
  ctx: JdbcDecodingContext,
  module: SerializersModule,
  initialRowIndex: Int,
  api: ApiDecoders<Connection, ResultSet>,
  decoders: Set<SqlDecoder<Connection, ResultSet, out Any>>,
  type: RowDecoderType,
  json: Json,
  endCallback: (Int) -> Unit
): RowDecoder<Connection, ResultSet>(ctx, module, initialRowIndex, api, decoders, type, json, endCallback) {

  companion object {
    operator fun invoke(
      ctx: JdbcDecodingContext,
      module: SerializersModule,
      api: ApiDecoders<Connection, ResultSet>,
      decoders: Set<SqlDecoder<Connection, ResultSet, out Any>>,
      descriptor: SerialDescriptor,
      json: Json
    ): JdbcRowDecoder {
      // If the column infos actaully exist, then verify them
      ctx.columnInfos?.let { columns -> descriptor.verifyColumns(columns) }
      return JdbcRowDecoder(ctx, module, 1, api, decoders, RowDecoderType.Regular, json, {})
    }
  }

  override fun cloneSelf(ctx: JdbcDecodingContext, initialRowIndex: Int, type: RowDecoderType, endCallback: (Int) -> Unit): RowDecoder<Connection, ResultSet> =
    JdbcRowDecoder(ctx, this.serializersModule, initialRowIndex, api, decoders, type, json, endCallback)
}
