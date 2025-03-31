package io.exoquery.controller.jdbc

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

fun TerpalDriver.Postgres.Companion.fromConfig(prefix: String) =
  TerpalDriver.Postgres(HikariHelper.makeDataSource(prefix))

fun TerpalDriver.PostgresLegacy.Companion.fromConfig(prefix: String) =
  TerpalDriver.PostgresLegacy(HikariHelper.makeDataSource(prefix))

fun TerpalDriver.H2.Companion.fromConfig(prefix: String) =
  TerpalDriver.H2(HikariHelper.makeDataSource(prefix))

fun TerpalDriver.Mysql.Companion.fromConfig(prefix: String) =
  TerpalDriver.Mysql(HikariHelper.makeDataSource(prefix))

fun TerpalDriver.Sqlite.Companion.fromConfig(prefix: String) =
  TerpalDriver.Sqlite(HikariHelper.makeDataSource(prefix))

fun TerpalDriver.SqlServer.Companion.fromConfig(prefix: String) =
  TerpalDriver.SqlServer(HikariHelper.makeDataSource(prefix))

fun TerpalDriver.Oracle.Companion.fromConfig(prefix: String) =
  TerpalDriver.Oracle(HikariHelper.makeDataSource(prefix))
