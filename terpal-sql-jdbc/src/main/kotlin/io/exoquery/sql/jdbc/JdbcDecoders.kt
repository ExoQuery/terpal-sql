package io.exoquery.sql.jdbc

import io.exoquery.sql.DecoderAny
import io.exoquery.sql.DecodingContext
import io.exoquery.sql.SqlDecoder
import java.awt.event.FocusEvent.Cause
import java.math.BigDecimal
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.time.*
import java.util.*
import kotlin.reflect.KClass

/** Represents a Jdbc Decoder with a nullable or non-nullable output value */
typealias JdbcDecoder<T> = SqlDecoder<Connection, ResultSet, T>

class JdbcDecoderAny<T: Any>(
  override val type: KClass<T>,
  override val f: (JdbcDecodingContext, Int) -> T
): DecoderAny<T, Connection, ResultSet>(
    type,
    { index, row ->
      row.getObject(index)
      row.wasNull()
    },
    f
  )
