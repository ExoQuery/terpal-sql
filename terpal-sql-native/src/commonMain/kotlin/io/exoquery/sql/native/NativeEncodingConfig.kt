package io.exoquery.sql.native

import io.exoquery.sql.EncodingConfig
import io.exoquery.sql.SqlDecoder
import io.exoquery.sql.SqlEncoder
import io.exoquery.sql.sqlite.SqliteCursorWrapper
import io.exoquery.sql.sqlite.SqliteStatementWrapper
import io.exoquery.sql.sqlite.Unused
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

data class NativeEncodingConfig(
  override val additionalEncoders: Set<SqlEncoder<Unused, SqliteStatementWrapper, out Any>> = setOf(),
  override val additionalDecoders: Set<SqlDecoder<Unused, SqliteCursorWrapper, out Any>> = setOf(),
  override val json: Json = Json,
  override val module: SerializersModule = EmptySerializersModule(),
  override val timezone: TimeZone = TimeZone.currentSystemDefault()
) : EncodingConfig<Unused, SqliteStatementWrapper, SqliteCursorWrapper>