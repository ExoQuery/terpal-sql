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
  }

}
