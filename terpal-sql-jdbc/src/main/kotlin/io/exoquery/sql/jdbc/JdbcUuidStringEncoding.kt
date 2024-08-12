package io.exoquery.sql.jdbc

import io.exoquery.sql.SqlDecoder
import io.exoquery.sql.SqlEncoder
import io.exoquery.sql.JavaUuidEncoding
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.*

object JdbcUuidStringEncoding: JavaUuidEncoding<Connection, PreparedStatement, ResultSet> {
  override val JUuidEncoder: SqlEncoder<Connection, PreparedStatement, UUID> =
    //JdbcEncoderAny.fromFunction(java.sql.Types.VARCHAR) { ctx, v, i -> ctx.stmt.setString(i, v.toString()) }
    JdbcEncoderAny(java.sql.Types.OTHER, UUID::class) { ctx, v, i -> ctx.stmt.setObject(i, v) }

  override val JUuidDecoder: SqlDecoder<Connection, ResultSet, UUID> =
    //JdbcDecoderAny.fromFunction { ctx, i -> UUID.fromString(ctx.row.getString(i)) }
    JdbcDecoderAny(UUID::class) { ctx, i -> UUID.fromString(ctx.row.getString(i)) }
}

object JdbcUuidObjectEncoding: JavaUuidEncoding<Connection, PreparedStatement, ResultSet> {
  override val JUuidEncoder: SqlEncoder<Connection, PreparedStatement, UUID> =
    //JdbcEncoderAny.fromFunction(java.sql.Types.OTHER) { ctx, v, i -> ctx.stmt.setObject(i, v) }
    JdbcEncoderAny(java.sql.Types.OTHER, UUID::class) { ctx, v, i -> ctx.stmt.setObject(i, v) }

  override val JUuidDecoder: SqlDecoder<Connection, ResultSet, UUID> =
    //JdbcDecoderAny.fromFunction { ctx, i -> ctx.row.getObject(i, UUID::class.java) }
    JdbcDecoderAny(UUID::class) { ctx, i -> ctx.row.getObject(i, UUID::class.java) }
}
