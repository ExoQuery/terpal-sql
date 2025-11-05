package io.exoquery.controller.r2dbc

import io.exoquery.controller.EncodingConfig
import io.exoquery.controller.SqlDecoder
import io.exoquery.controller.SqlEncoder
import io.r2dbc.spi.Connection
import io.r2dbc.spi.Row
import io.r2dbc.spi.Statement
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Mirrors JdbcEncodingConfig: provides factory helpers and defaults that include built-in
 * R2DBC encoders/decoders unless Empty() is used.
 */
 data class R2dbcEncodingConfig private constructor(
  override val additionalEncoders: Set<SqlEncoder<Connection, Statement, out Any>>,
  override val additionalDecoders: Set<SqlDecoder<Connection, Row, out Any>>,
  override val json: Json,
  override val module: SerializersModule,
  override val timezone: TimeZone,
  override val debugMode: Boolean
): EncodingConfig<Connection, Statement, Row> {
  companion object {
    val Default get() =
      Default(
        R2dbcEncoders.encoders,
        R2dbcDecoders.decoders
      )

    fun Default(
      additionalEncoders: Set<SqlEncoder<Connection, Statement, out Any>> = setOf(),
      additionalDecoders: Set<SqlDecoder<Connection, Row, out Any>> = setOf(),
      json: Json = Json,
      module: SerializersModule = EmptySerializersModule(),
      timezone: TimeZone = TimeZone.currentSystemDefault(),
      debugMode: Boolean = false
    ) = R2dbcEncodingConfig(
      additionalEncoders + R2dbcEncoders.encoders,
      additionalDecoders + R2dbcDecoders.decoders,
      json,
      module,
      timezone,
      debugMode
    )

    operator fun invoke(
      additionalEncoders: Set<SqlEncoder<Connection, Statement, out Any>> = setOf(),
      additionalDecoders: Set<SqlDecoder<Connection, Row, out Any>> = setOf(),
      json: Json = Json,
      module: SerializersModule = EmptySerializersModule(),
      timezone: TimeZone = TimeZone.currentSystemDefault()
    ) = Default(additionalEncoders, additionalDecoders, json, module, timezone)

    fun Empty(
      additionalEncoders: Set<SqlEncoder<Connection, Statement, out Any>> = setOf(),
      additionalDecoders: Set<SqlDecoder<Connection, Row, out Any>> = setOf(),
      json: Json = Json,
      module: SerializersModule = EmptySerializersModule(),
      timezone: TimeZone = TimeZone.currentSystemDefault()
    ) = R2dbcEncodingConfig(additionalEncoders, additionalDecoders, json, module, timezone, debugMode = false)
  }
}
