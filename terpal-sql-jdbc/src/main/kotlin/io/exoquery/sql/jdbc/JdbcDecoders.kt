package io.exoquery.sql.jdbc

import io.exoquery.sql.SqlDecoder
import java.awt.event.FocusEvent.Cause
import java.math.BigDecimal
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.time.*
import java.util.*
import kotlin.reflect.KClass

data class EncodingException(val msg: String, val errorCause: Throwable? = null): SQLException(msg.toString(), errorCause) {
  override fun toString(): String = msg
}

/** Represents a Jdbc Decoder with a nullable or non-nullable output value */
typealias JdbcDecoder<T> = SqlDecoder<Connection, ResultSet, T>

/** Represents a Jdbc Decoder with a non-nullable output value */
class JdbcDecoderAny<T: Any>(override val type: KClass<T>, val f: (JdbcDecodingContext, Int) -> T): JdbcDecoder<T>() {
  override fun decode(ctx: JdbcDecodingContext, index: Int): T =
    f(ctx, index)

  inline fun <reified R: Any> map(crossinline f: (T) -> R): JdbcDecoderAny<R> =
    JdbcDecoderAny<R>(R::class) { ctx, index -> f(this.decode(ctx, index)) }

  override fun asNullable(): SqlDecoder<Connection, ResultSet, T?> =
    object: SqlDecoder<Connection, ResultSet, T?>() {
      override fun asNullable(): SqlDecoder<Connection, ResultSet, T?> = this
      override val type = this@JdbcDecoderAny.type
      override fun decode(ctx: JdbcDecodingContext, index: Int): T? {
        ctx.row.getObject(index)
        return if (ctx.row.wasNull())
          null
        else
          this@JdbcDecoderAny.decode(ctx, index)
      }
    }
}
