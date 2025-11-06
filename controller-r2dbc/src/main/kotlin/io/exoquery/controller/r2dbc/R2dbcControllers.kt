package io.exoquery.controller.r2dbc

import io.exoquery.controller.BasicEncoding
import io.exoquery.controller.JavaSqlEncoding
import io.exoquery.controller.JavaTimeEncoding
import io.exoquery.controller.JavaUuidEncoding
import io.exoquery.controller.SqlDecoder
import io.exoquery.controller.SqlEncoder
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Row
import io.r2dbc.spi.Statement

object R2dbcControllers {
  class Postgres(
    encodingConfig: R2dbcEncodingConfig = R2dbcEncodingConfig.Default(),
    override val connectionFactory: ConnectionFactory
  ): R2dbcController(encodingConfig,connectionFactory) {

    override val encodingConfig =
      encodingConfig.copy(
        additionalEncoders = encodingConfig.additionalEncoders + R2dbcPostgresAdditionalEncoding.encoders,
        additionalDecoders = encodingConfig.additionalDecoders + R2dbcPostgresAdditionalEncoding.decoders
      )

    override val encodingApi: R2dbcSqlEncoding =
      object: JavaSqlEncoding<Connection, Statement, Row>,
        BasicEncoding<Connection, Statement, Row> by R2dbcBasicEncoding,
        JavaTimeEncoding<Connection, Statement, Row> by R2dbcTimeEncoding,
        JavaUuidEncoding<Connection, Statement, Row> by R2dbcUuidEncoding {}

    override protected fun changePlaceholders(sql: String): String {
      // Postgres R2DBC uses $1, $2... for placeholders.
      // Most other R2DBC drivers (e.g. MSSQL) use '?', so do not rewrite for them.
      val sb = StringBuilder()
      var paramIndex = 1
      var i = 0
      while (i < sql.length) {
        val c = sql[i]
        if (c == '?') {
          sb.append('$').append(paramIndex)
          paramIndex++
          i++
        } else {
          sb.append(c)
          i++
        }
      }
      return sb.toString()
    }
  }

  class SqlServer(
    encodingConfig: R2dbcEncodingConfig = R2dbcEncodingConfig.Default(),
    override val connectionFactory: ConnectionFactory
  ): R2dbcController(encodingConfig,connectionFactory) {
    override protected fun changePlaceholders(sql: String): String {
      // MSSQL R2DBC uses @1, @2... for placeholders.
      // Most other R2DBC drivers (e.g. MSSQL) use '?', so do not rewrite for them.
      val sb = StringBuilder()
      var paramIndex = 0
      var i = 0
      while (i < sql.length) {
        val c = sql[i]
        if (c == '?') {
          // Params are named like @Param0, @Param1, ... parameter
          // binding is indexed based. SqlServer R2DBC supports this.
          sb.append("@Param${paramIndex}")
          paramIndex++
          i++
        } else {
          sb.append(c)
          i++
        }
      }
      return sb.toString()
    }
  }
}
