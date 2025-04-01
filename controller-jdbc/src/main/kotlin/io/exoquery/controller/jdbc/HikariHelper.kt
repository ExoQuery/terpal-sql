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

fun DatabaseController.Postgres.Companion.fromConfig(prefix: String) =
  DatabaseController.Postgres(HikariHelper.makeDataSource(prefix))

fun DatabaseController.PostgresLegacy.Companion.fromConfig(prefix: String) =
  DatabaseController.PostgresLegacy(HikariHelper.makeDataSource(prefix))

fun DatabaseController.H2.Companion.fromConfig(prefix: String) =
  DatabaseController.H2(HikariHelper.makeDataSource(prefix))

fun DatabaseController.Mysql.Companion.fromConfig(prefix: String) =
  DatabaseController.Mysql(HikariHelper.makeDataSource(prefix))

fun DatabaseController.Sqlite.Companion.fromConfig(prefix: String) =
  DatabaseController.Sqlite(HikariHelper.makeDataSource(prefix))

fun DatabaseController.SqlServer.Companion.fromConfig(prefix: String) =
  DatabaseController.SqlServer(HikariHelper.makeDataSource(prefix))

fun DatabaseController.Oracle.Companion.fromConfig(prefix: String) =
  DatabaseController.Oracle(HikariHelper.makeDataSource(prefix))
