package io.exoquery.controller.native

import io.exoquery.controller.EncodingConfig
import io.exoquery.controller.SqlDecoder
import io.exoquery.controller.SqlEncoder
import io.exoquery.controller.sqlite.SqliteCursorWrapper
import io.exoquery.controller.sqlite.SqliteStatementWrapper
import io.exoquery.controller.sqlite.Unused
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

data class NativeEncodingConfig(
  override val additionalEncoders: Set<SqlEncoder<Unused, SqliteStatementWrapper, out Any>> = setOf(),
  override val additionalDecoders: Set<SqlDecoder<Unused, SqliteCursorWrapper, out Any>> = setOf(),
  override val json: Json = Json,
  override val module: SerializersModule = EmptySerializersModule(),
  override val timezone: TimeZone = TimeZone.currentSystemDefault(),
  override val debugMode: Boolean = false
) : EncodingConfig<Unused, SqliteStatementWrapper, SqliteCursorWrapper>
