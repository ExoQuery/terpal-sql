package io.exoquery.sql

import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactories
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres

object TestDatabasesR2dbc {
  val embeddedPostgres: EmbeddedPostgres by lazy {
    val started = EmbeddedPostgres.builder().start()
    val postgresScriptsPath = "/db/postgres-schema.sql"
    val resource = this::class.java.getResource(postgresScriptsPath)
    if (resource == null) throw NullPointerException("The postgres script path `$postgresScriptsPath` was not found")
    val postgresScript = resource.readText()
    //started.postgresDatabase.run(postgresScript)
    started
  }

  val postgres: ConnectionFactory by lazy {
    val ep = embeddedPostgres
    val host = "localhost"
    val port = ep.port
    val db = "postgres"
    val user = "postgres"
    ConnectionFactories.get(
      io.r2dbc.spi.ConnectionFactoryOptions.builder()
        .option(io.r2dbc.spi.ConnectionFactoryOptions.DRIVER, "postgresql")
        .option(io.r2dbc.spi.ConnectionFactoryOptions.HOST, host)
        .option(io.r2dbc.spi.ConnectionFactoryOptions.PORT, port)
        .option(io.r2dbc.spi.ConnectionFactoryOptions.DATABASE, db)
        .option(io.r2dbc.spi.ConnectionFactoryOptions.USER, user)
        // Provide password if needed; EmbeddedPostgres default often doesn't require it
        // .option(io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD, "password")
        .build()
    )
  }
}
