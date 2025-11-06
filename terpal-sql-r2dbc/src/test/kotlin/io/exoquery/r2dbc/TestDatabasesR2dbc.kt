package io.exoquery.r2dbc

import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres

object TestDatabasesR2dbc {
  val embeddedPostgres: EmbeddedPostgres by lazy {
    val started = EmbeddedPostgres.builder().start()
    val postgresScriptsPath = "/db/postgres-schema.sql"
    val resource = this::class.java.getResource(postgresScriptsPath)
    if (resource == null) throw NullPointerException("The postgres script path `$postgresScriptsPath` was not found")
    val postgresScript = resource.readText()
    started.postgresDatabase.connection.use { conn ->
      val commands = postgresScript.split(';')
      commands.filter { it.isNotBlank() }.forEach { cmd ->
        conn.prepareStatement(cmd).execute()
      }
    }
    started
  }

  val postgres: ConnectionFactory by lazy {
    val ep = embeddedPostgres
    val host = "localhost"
    val port = ep.port
    val db = "postgres"
    val user = "postgres"
    ConnectionFactories.get(
      ConnectionFactoryOptions.builder()
        .option(ConnectionFactoryOptions.DRIVER, "postgresql")
        .option(ConnectionFactoryOptions.HOST, host)
        .option(ConnectionFactoryOptions.PORT, port)
        .option(ConnectionFactoryOptions.DATABASE, db)
        .option(ConnectionFactoryOptions.USER, user)
        // Provide password if needed; EmbeddedPostgres default often doesn't require it
        // .option(io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD, "password")
        .build()
    )
  }

  val sqlServer: ConnectionFactory by lazy {
    // Matches docker-compose and setup scripts
    ConnectionFactories.get(
      ConnectionFactoryOptions.builder()
        .option(ConnectionFactoryOptions.DRIVER, "mssql")
        .option(ConnectionFactoryOptions.HOST, "localhost")
        .option(ConnectionFactoryOptions.PORT, 31433)
        .option(ConnectionFactoryOptions.DATABASE, "exoquery_test")
        .option(ConnectionFactoryOptions.USER, "sa")
        .option(ConnectionFactoryOptions.PASSWORD, "ExoQueryRocks!")
        .build()
    )
  }
}
