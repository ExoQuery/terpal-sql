package io.exoquery.sql.jdbc

import com.typesafe.config.ConfigFactory
import com.zaxxer.hikari.HikariDataSource

object HikariHelper {
  fun makeDataSource(configPrefix: String): HikariDataSource {
    val factory = ConfigFactory.load(this::class.java.classLoader)
    val config =
      if (factory.hasPath(configPrefix))
        factory.getConfig(configPrefix)
      else
        ConfigFactory.empty()
    return JdbcContextConfig(config).dataSource()
  }
}

fun TerpalContext.Postgres.Companion.fromConfig(prefix: String) =
  TerpalContext.Postgres(HikariHelper.makeDataSource(prefix))

fun TerpalContext.PostgresLegacy.Companion.fromConfig(prefix: String) =
  TerpalContext.PostgresLegacy(HikariHelper.makeDataSource(prefix))

fun TerpalContext.H2.Companion.fromConfig(prefix: String) =
  TerpalContext.H2(HikariHelper.makeDataSource(prefix))

fun TerpalContext.Mysql.Companion.fromConfig(prefix: String) =
  TerpalContext.Mysql(HikariHelper.makeDataSource(prefix))

fun TerpalContext.Sqlite.Companion.fromConfig(prefix: String) =
  TerpalContext.Sqlite(HikariHelper.makeDataSource(prefix))

fun TerpalContext.SqlServer.Companion.fromConfig(prefix: String) =
  TerpalContext.SqlServer(HikariHelper.makeDataSource(prefix))

fun TerpalContext.Oracle.Companion.fromConfig(prefix: String) =
  TerpalContext.Oracle(HikariHelper.makeDataSource(prefix))
