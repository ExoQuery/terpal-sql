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

fun JdbcControllers.Postgres.Companion.fromConfig(prefix: String) =
  JdbcControllers.Postgres(HikariHelper.makeDataSource(prefix))

fun JdbcControllers.PostgresLegacy.Companion.fromConfig(prefix: String) =
  JdbcControllers.PostgresLegacy(HikariHelper.makeDataSource(prefix))

fun JdbcControllers.H2.Companion.fromConfig(prefix: String) =
  JdbcControllers.H2(HikariHelper.makeDataSource(prefix))

fun JdbcControllers.Mysql.Companion.fromConfig(prefix: String) =
  JdbcControllers.Mysql(HikariHelper.makeDataSource(prefix))

fun JdbcControllers.Sqlite.Companion.fromConfig(prefix: String) =
  JdbcControllers.Sqlite(HikariHelper.makeDataSource(prefix))

fun JdbcControllers.SqlServer.Companion.fromConfig(prefix: String) =
  JdbcControllers.SqlServer(HikariHelper.makeDataSource(prefix))

fun JdbcControllers.Oracle.Companion.fromConfig(prefix: String) =
  JdbcControllers.Oracle(HikariHelper.makeDataSource(prefix))
