package io.exoquery.controller.jdbc

import io.exoquery.controller.EncodingConfig
import io.exoquery.controller.SqlDecoder
import io.exoquery.controller.SqlEncoder
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * Most constructions will want to specify default values from AdditionalJdbcEncoding for additionalEncoders/decoders,
 * and they should have a simple construction JdbcEncodingConfig(...). Use `Empty` to make a config that does not
 * include these defaults. For this reason the real constructor is private.
 */
data class JdbcEncodingConfig private constructor(
  override val additionalEncoders: Set<SqlEncoder<Connection, PreparedStatement, out Any>>,
  override val additionalDecoders: Set<SqlDecoder<Connection, ResultSet, out Any>>,
  override val json: Json,
  // If you want to use any primitive-wrapped contextual encoders you need to add them here
  override val module: SerializersModule,
  override val timezone: TimeZone, override val debugMode: Boolean
): EncodingConfig<Connection, PreparedStatement, ResultSet> {
  companion object {
    val Default get() =
      Default(
        AdditionalJdbcEncoding.encoders,
        AdditionalJdbcEncoding.decoders
      )

    fun Default(
      additionalEncoders: Set<SqlEncoder<Connection, PreparedStatement, out Any>> = setOf(),
      additionalDecoders: Set<SqlDecoder<Connection, ResultSet, out Any>> = setOf(),
      json: Json = Json.Default,
      module: SerializersModule = EmptySerializersModule(),
      timezone: TimeZone = TimeZone.Companion.currentSystemDefault(),
      debugMode: Boolean = false
    ) = JdbcEncodingConfig(
      additionalEncoders + AdditionalJdbcEncoding.encoders,
      additionalDecoders + AdditionalJdbcEncoding.decoders,
      json,
      module,
      timezone,
      debugMode
    )

    operator fun invoke(
      additionalEncoders: Set<SqlEncoder<Connection, PreparedStatement, out Any>> = setOf(),
      additionalDecoders: Set<SqlDecoder<Connection, ResultSet, out Any>> = setOf(),
      json: Json = Json.Default,
      module: SerializersModule = EmptySerializersModule(),
      timezone: TimeZone = TimeZone.Companion.currentSystemDefault()
    ) = Default(additionalEncoders, additionalDecoders, json, module, timezone)

    fun Empty(
      additionalEncoders: Set<SqlEncoder<Connection, PreparedStatement, out Any>> = setOf(),
      additionalDecoders: Set<SqlDecoder<Connection, ResultSet, out Any>> = setOf(),
      json: Json = Json.Default,
      module: SerializersModule = EmptySerializersModule(),
      timezone: TimeZone = TimeZone.Companion.currentSystemDefault()
    ) = JdbcEncodingConfig(additionalEncoders, additionalDecoders, json, module, timezone)
  }
}
