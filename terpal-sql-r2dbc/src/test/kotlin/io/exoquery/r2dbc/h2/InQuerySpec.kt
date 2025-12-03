package io.exoquery.r2dbc.h2

import io.exoquery.controller.TerpalSqlUnsafe
import io.exoquery.controller.r2dbc.R2dbcController
import io.exoquery.controller.r2dbc.R2dbcControllers
import io.exoquery.controller.runActionsUnsafe
import io.exoquery.controller.runOn
import io.exoquery.r2dbc.TestDatabasesR2dbc
import io.exoquery.sql.Params
import io.exoquery.sql.Sql
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable

class InQuerySpec : FreeSpec({

  val cf = TestDatabasesR2dbc.h2
  val ctx: R2dbcController by lazy { R2dbcControllers.H2(connectionFactory = cf) }

  @OptIn(TerpalSqlUnsafe::class)
  suspend fun runActions(actions: String) = ctx.runActionsUnsafe(actions)

  beforeSpec {
    runActions(
      """
      DELETE FROM Person;
      DELETE FROM Address;
      INSERT INTO Person (id, firstName, lastName, age) VALUES (1, 'Joe', 'Bloggs', 111);
      INSERT INTO Person (id, firstName, lastName, age) VALUES (2, 'Jim', 'Roogs', 222);
      INSERT INTO Person (id, firstName, lastName, age) VALUES (3, 'Jill', 'Doogs', 222);
      INSERT INTO Address (ownerId, street, zip) VALUES (1, '123 Main St', '12345');
      """.trimIndent()
    )
  }

  @Serializable
  data class Person(val id: Int, val firstName: String, val lastName: String, val age: Int)

  "Person IN (names) - simple" {
    val sql = Sql("SELECT id, firstName, lastName, age FROM Person WHERE firstName IN ${Params("Joe", "Jim")}").queryOf<Person>()
    sql.sql shouldBe "SELECT id, firstName, lastName, age FROM Person WHERE firstName IN (?, ?)"
    sql.runOn(ctx) shouldBe listOf(
      Person(1, "Joe", "Bloggs", 111),
      Person(2, "Jim", "Roogs", 222)
    )
  }

  "Person IN (names) - single" {
    val sql = Sql("SELECT id, firstName, lastName, age FROM Person WHERE firstName IN ${Params("Joe")}").queryOf<Person>()
    sql.sql shouldBe "SELECT id, firstName, lastName, age FROM Person WHERE firstName IN (?)"
    sql.runOn(ctx) shouldBe listOf(
      Person(1, "Joe", "Bloggs", 111)
    )
  }

  "Person IN (names) - empty" {
    val sql = Sql("SELECT id, firstName, lastName, age FROM Person WHERE firstName IN ${Params.empty()}").queryOf<Person>()
    sql.sql shouldBe "SELECT id, firstName, lastName, age FROM Person WHERE firstName IN (null)"
    sql.runOn(ctx) shouldBe listOf()
  }

  "Person IN (names) - empty list" {
    val names: List<String> = emptyList()
    Sql("SELECT id, firstName, lastName, age FROM Person WHERE firstName IN ${Params.list(names)}").queryOf<Person>().runOn(ctx) shouldBe listOf()
  }
})
