package io.exoquery.r2dbc.sqlserver

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
  val cf = TestDatabasesR2dbc.sqlServer
  val ctx: R2dbcController by lazy { R2dbcControllers.SqlServer(connectionFactory = cf) }

  suspend fun runActions(actions: String) = ctx.runActions(actions)

  beforeEach {
    runActions(
      """
      TRUNCATE TABLE Person; DBCC CHECKIDENT ('Person', RESEED, 1);
      TRUNCATE TABLE Address;
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

  "Insert Returning Record via OUTPUT" {
    val person1 = Sql("INSERT INTO Person (firstName, lastName, age) OUTPUT INSERTED.id, INSERTED.firstName, INSERTED.lastName, INSERTED.age VALUES (${joe.firstName}, ${joe.lastName}, ${joe.age})").actionReturning<Person>().runOn(ctx)
    val person2 = Sql("INSERT INTO Person (firstName, lastName, age) OUTPUT INSERTED.id, INSERTED.firstName, INSERTED.lastName, INSERTED.age VALUES (${jim.firstName}, ${jim.lastName}, ${jim.age})").actionReturning<Person>().runOn(ctx)
    person1 shouldBe joe
    person2 shouldBe jim
    Sql("SELECT id, firstName, lastName, age FROM Person").queryOf<Person>().runOn(ctx) shouldBe listOf(joe, jim)
  }
})
