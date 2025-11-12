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

  val mysql: ConnectionFactory by lazy {
    // Matches docker-compose and setup scripts for MySQL
    ConnectionFactories.get(
      ConnectionFactoryOptions.builder()
        .option(ConnectionFactoryOptions.DRIVER, "mysql")
        .option(ConnectionFactoryOptions.HOST, "localhost")
        .option(ConnectionFactoryOptions.PORT, 33306)
        .option(ConnectionFactoryOptions.DATABASE, "exoquery_test")
        .option(ConnectionFactoryOptions.USER, "root")
        .option(ConnectionFactoryOptions.PASSWORD, "root")
        // SSL disabled for local docker testing
        // .option(Option.valueOf("sslMode"), "disable")
        .build()
    )
  }

  val h2: ConnectionFactory by lazy {
    // A private DB via jdbc:h2:mem is not possible as R2DBC H2. A DB name is required.
    // Since the init-script runs every time a new connection is made, we need to
    // add CREATE TABLE IF NOT EXISTS to everything in db/h2-schema.sql.
    ConnectionFactories.get("r2dbc:h2:mem:///exoquery_test;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT%20FROM%20'classpath:db/h2-schema.sql'")
  }

  val oracle: ConnectionFactory by lazy {
    // Matches docker-compose and setup scripts for Oracle
    ConnectionFactories.get(
      ConnectionFactoryOptions.builder()
        .option(ConnectionFactoryOptions.DRIVER, "oracle")
        .option(ConnectionFactoryOptions.HOST, "localhost")
        .option(ConnectionFactoryOptions.PORT, 31521)
        .option(ConnectionFactoryOptions.DATABASE, "xe")
        .option(ConnectionFactoryOptions.USER, "exoquery_test")
        .option(ConnectionFactoryOptions.PASSWORD, "ExoQueryRocks!")
        .build()
    )
  }
}
