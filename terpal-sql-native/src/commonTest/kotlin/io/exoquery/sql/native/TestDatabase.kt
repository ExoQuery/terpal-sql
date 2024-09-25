package io.exoquery.sql.native

import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.DatabaseFileContext.deleteDatabase
import co.touchlab.sqliter.createDatabaseManager
import co.touchlab.sqliter.withConnection
import io.exoquery.sql.BasicSchema
import io.exoquery.sql.WalTestSchema
import io.exoquery.sql.executeSimple
import kotlinx.coroutines.runBlocking

object WalTestDatabase {
  val name = "wal_test.db"
  val basePath = "./"

  val ctx by lazy {
    runBlocking {
      deleteDatabase(name)
      TerpalNativeContext.fromSchema(WalTestSchema, name, basePath, mode = TerpalNativeContext.PoolingMode.Multiple(3))
    }
  }
}

object TestDatabase {
  val name = "terpal_test.db"
  //val basePath = "/home/alexi/git/terpal-sql/terpal-sql-native/"
  val basePath = "./"
  val ctx by lazy {
    runBlocking {
      deleteDatabase(name, basePath)
      TerpalNativeContext.fromSchema(BasicSchema, name, basePath)
    }
  }

  fun run(query: String) {
    createDatabaseManager(emptyDatabaseConfig()).withConnection {
      wrapConnection(it) {
        query.split(";").forEach { queryPart ->
          it.executeSimple(queryPart)
        }
      }
    }
  }

  fun emptyDatabaseConfig() = run {
    DatabaseConfiguration(
      name = name,
      version = 1,
      create = { connection -> wrapConnection(connection) { Unit } },
      upgrade = { connection, oldVersion, newVersion -> Unit }
    )
  }

}
