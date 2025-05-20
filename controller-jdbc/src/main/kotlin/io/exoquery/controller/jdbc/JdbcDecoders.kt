package io.exoquery.controller.jdbc

import io.exoquery.controller.DecoderAny
import io.exoquery.controller.SqlDecoder
import java.sql.Connection
import java.sql.ResultSet
import kotlin.reflect.KClass

/** Represents a Jdbc Decoder with a nullable or non-nullable output value */
typealias JdbcDecoder<T> = SqlDecoder<Connection, ResultSet, T>

class JdbcDecoderAny<T: Any>(
  override val type: KClass<T>,
  override val f: (JdbcDecodingContext, Int) -> T?
): DecoderAny<T, Connection, ResultSet>(
    type,
    { index, row ->
      row.getObject(index)
      row.wasNull()
    },
    f
  ) {
}
