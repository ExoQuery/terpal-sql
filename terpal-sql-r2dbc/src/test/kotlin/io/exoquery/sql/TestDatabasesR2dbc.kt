package io.exoquery.sql

import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactories
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres

object TestDatabasesR2dbc {
  val embeddedPostgres: EmbeddedPostgres by lazy {
    val started = EmbeddedPostgres.start()
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
    val password = "postgres"
    val url = "r2dbc:postgresql://$user:$password@$host:$port/$db"
    ConnectionFactories.get(url)
  }
}
