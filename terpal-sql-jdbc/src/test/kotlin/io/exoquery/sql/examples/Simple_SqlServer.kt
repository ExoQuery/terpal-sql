package io.exoquery.sql.examples

import io.exoquery.sql.Sql
import io.exoquery.controller.jdbc.JdbcControllers
import io.exoquery.controller.jdbc.fromConfig
import io.exoquery.controller.runOn
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

object Simple_SqlServer {
  @Serializable
  data class Person(val id: Int, val firstName: String, val lastName: String, val age: Int)
}


fun main() {
  runBlocking {
    val ctx = JdbcControllers.SqlServer.fromConfig("testMysqlDB")
    Sql("DELETE FROM Person").action().runOn(ctx)
    Sql("INSERT INTO Person (firstName, lastName, age) VALUES ('Joe', 'Bloggs', 111)").action().runOn(ctx)
    Sql("SELECT * FROM Person").queryOf<Simple_SqlServer.Person>().runOn(ctx).forEach {
      println(it)
    }
  }
}
