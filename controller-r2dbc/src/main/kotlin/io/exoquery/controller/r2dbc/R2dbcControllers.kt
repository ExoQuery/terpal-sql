package io.exoquery.controller.r2dbc

import io.exoquery.controller.BasicEncoding
import io.exoquery.controller.JavaSqlEncoding
import io.exoquery.controller.JavaTimeEncoding
import io.exoquery.controller.JavaUuidEncoding
import io.exoquery.controller.StartingIndex
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
        JavaUuidEncoding<Connection, Statement, Row> by R2dbcUuidEncodingNative {}

    override protected fun changePlaceholders(sql: String): String =
      changePlaceholdersIn(sql) { index -> "$${index + 1}" }
  }

  class SqlServer(
    encodingConfig: R2dbcEncodingConfig = R2dbcEncodingConfig.Default(),
    override val connectionFactory: ConnectionFactory
  ): R2dbcController(encodingConfig,connectionFactory) {

    override val encodingApi: R2dbcSqlEncoding =
      object: JavaSqlEncoding<Connection, Statement, Row>,
        BasicEncoding<Connection, Statement, Row> by R2dbcBasicEncoding,
        JavaTimeEncoding<Connection, Statement, Row> by R2dbcTimeEncodingSqlServer,
        JavaUuidEncoding<Connection, Statement, Row> by R2dbcUuidEncodingString {}

    /** Change the names of the variable params so they can be used by the SQL Server R2DBC driver
     * The SQL Server R2DBC driver supports named-parameter binding i.e. row.bind("@firstName", value)
     * as well as positional binding i.e. row.bind(0, value). When positional binding is done, the names
     * of ther parameters in the SQL string are ignored. Since we are using positional binding,
     * we can use any names we want so we want to choose names that are user friendly to debug.
     * Therefore we choose @ParamX where X is the index-kind that the context actually uses.
     */
    override protected fun changePlaceholders(sql: String): String =
      changePlaceholdersIn(sql) { index -> "@Param${index + startingStatementIndex.value}" }
  }

  class Mysql(
    encodingConfig: R2dbcEncodingConfig = R2dbcEncodingConfig.Default(),
    override val connectionFactory: ConnectionFactory
  ): R2dbcController(encodingConfig, connectionFactory) {

    override val encodingApi: R2dbcSqlEncoding =
      object: JavaSqlEncoding<Connection, Statement, Row>,
        BasicEncoding<Connection, Statement, Row> by R2dbcBasicEncoding,
        JavaTimeEncoding<Connection, Statement, Row> by R2dbcTimeEncoding,
        JavaUuidEncoding<Connection, Statement, Row> by R2dbcUuidEncodingString {}

    // MySQL R2DBC uses '?' positional parameters, so no change
    override fun changePlaceholders(sql: String): String = sql
  }

  class H2(
    encodingConfig: R2dbcEncodingConfig = R2dbcEncodingConfig.Default(),
    override val connectionFactory: ConnectionFactory
  ): R2dbcController(encodingConfig, connectionFactory) {

    override val startingResultRowIndex: StartingIndex get() = StartingIndex.Zero

    override val encodingApi: R2dbcSqlEncoding =
      object: JavaSqlEncoding<Connection, Statement, Row>,
        BasicEncoding<Connection, Statement, Row> by R2dbcBasicEncodingH2, // Need to override Int encoders with Long
        JavaTimeEncoding<Connection, Statement, Row> by R2dbcTimeEncodingH2,
        JavaUuidEncoding<Connection, Statement, Row> by R2dbcUuidEncodingNative {}

    override protected fun changePlaceholders(sql: String): String =
      changePlaceholdersIn(sql) { index -> "$${index + 1}" }
  }

  class Oracle(
    encodingConfig: R2dbcEncodingConfig = R2dbcEncodingConfig.Default(),
    override val connectionFactory: ConnectionFactory
  ): R2dbcController(encodingConfig, connectionFactory) {

    override val encodingApi: R2dbcSqlEncoding =
      object: JavaSqlEncoding<Connection, Statement, Row>,
        BasicEncoding<Connection, Statement, Row> by R2dbcBasicEncodingOracle,
        JavaTimeEncoding<Connection, Statement, Row> by R2dbcTimeEncodingOracle,
        JavaUuidEncoding<Connection, Statement, Row> by R2dbcUuidEncodingString {}

    override protected fun changePlaceholders(sql: String): String =
      changePlaceholdersIn(sql) { index -> ":${index + 1}" }
  }
}
