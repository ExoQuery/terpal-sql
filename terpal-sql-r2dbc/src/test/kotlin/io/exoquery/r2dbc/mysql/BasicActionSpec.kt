package io.exoquery.r2dbc.mysql

import io.exoquery.controller.r2dbc.R2dbcController
import io.exoquery.controller.r2dbc.R2dbcControllers
import io.exoquery.controller.runActions
import io.exoquery.controller.runOn
import io.exoquery.r2dbc.TestDatabasesR2dbc
import io.exoquery.sql.Sql
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable

class BasicActionSpec : FreeSpec({
  val cf = TestDatabasesR2dbc.mysql
  val ctx: R2dbcController by lazy { R2dbcControllers.Mysql(connectionFactory = cf) }

  suspend fun runActions(actions: String) = ctx.runActions(actions)

  beforeEach {
    runActions(
      """
      DELETE FROM Person;
      ALTER TABLE Person AUTO_INCREMENT = 1;
      DELETE FROM Address;
      """.trimIndent()
    )
  }

  @Serializable
  data class Person(val id: Int, val firstName: String, val lastName: String, val age: Int)

  val joe = Person(1, "Joe", "Bloggs", 111)
  val jim = Person(2, "Jim", "Roogs", 222)

  "Basic Insert" {
    Sql("INSERT INTO Person (firstName, lastName, age) VALUES (${joe.firstName}, ${joe.lastName}, ${joe.age})").action().runOn(ctx)
    Sql("INSERT INTO Person (firstName, lastName, age) VALUES (${jim.firstName}, ${jim.lastName}, ${jim.age})").action().runOn(ctx)
    Sql("SELECT id, firstName, lastName, age FROM Person").queryOf<Person>().runOn(ctx) shouldBe listOf(joe, jim)
  }
})
