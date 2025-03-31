package io.exoquery.controller.jdbc

import io.exoquery.controller.SqlDecoder
import io.exoquery.controller.SqlEncoder
import io.exoquery.controller.JavaUuidEncoding
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.*

object JdbcUuidStringEncoding: JavaUuidEncoding<Connection, PreparedStatement, ResultSet> {
  override val JUuidEncoder: SqlEncoder<Connection, PreparedStatement, UUID> =
    JdbcEncoderAny(Types.VARCHAR, UUID::class) { ctx, v, i -> ctx.stmt.setString(i, v.toString()) }

  override val JUuidDecoder: SqlDecoder<Connection, ResultSet, UUID> =
    //JdbcDecoderAny.fromFunction { ctx, i -> UUID.fromString(ctx.row.getString(i)) }
    JdbcDecoderAny(UUID::class) { ctx, i -> UUID.fromString(ctx.row.getString(i)) }
}

object JdbcUuidObjectEncoding: JavaUuidEncoding<Connection, PreparedStatement, ResultSet> {
  override val JUuidEncoder: SqlEncoder<Connection, PreparedStatement, UUID> =
    //JdbcEncoderAny.fromFunction(java.sql.Types.OTHER) { ctx, v, i -> ctx.stmt.setObject(i, v) }
    JdbcEncoderAny(Types.OTHER, UUID::class) { ctx, v, i -> ctx.stmt.setObject(i, v) }

  override val JUuidDecoder: SqlDecoder<Connection, ResultSet, UUID> =
    //JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getObject(i, UUID::class.java) }
    JdbcDecoderAny(UUID::class) { ctx, i -> ctx.row.getObject(i, UUID::class.java) }
}
