package io.exoquery.sql.postgres

import io.exoquery.sql.Params
import io.exoquery.sql.TestDatabases
import io.exoquery.sql.jdbc.TerpalDriver
import io.exoquery.sql.Sql
import io.exoquery.sql.run
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable

class InQuerySpec : FreeSpec({

  val ds = TestDatabases.postgres
  val ctx by lazy { TerpalDriver.Postgres(ds)  }

  @Serializable
  data class Person(val id: Int, val firstName: String, val lastName: String, val age: Int)

  beforeSpec {
    ds.run(
      """
      DELETE FROM Person;
      DELETE FROM Address;
      INSERT INTO Person (id, firstName, lastName, age) VALUES (1, 'Joe', 'Bloggs', 111);
      INSERT INTO Person (id, firstName, lastName, age) VALUES (2, 'Jim', 'Roogs', 222);
      INSERT INTO Person (id, firstName, lastName, age) VALUES (3, 'Jill', 'Doogs', 222);
      INSERT INTO Address (ownerId, street, zip) VALUES (1, '123 Main St', '12345');
      """
    )
  }

  "Person IN (names) - simple" {
    val sql = Sql("SELECT id, firstName, lastName, age FROM Person WHERE firstName IN ${Params("Joe", "Jim")}").queryOf<Person>()
    sql.sql shouldBe "SELECT id, firstName, lastName, age FROM Person WHERE firstName IN (?, ?)"
    ctx.run(sql) shouldBe listOf(
      Person(1, "Joe", "Bloggs", 111),
      Person(2, "Jim", "Roogs", 222)
    )
  }

  "Person IN (names) - single" {
    val sql = Sql("SELECT id, firstName, lastName, age FROM Person WHERE firstName IN ${Params("Joe")}").queryOf<Person>()
    sql.sql shouldBe "SELECT id, firstName, lastName, age FROM Person WHERE firstName IN (?)"
    ctx.run(sql) shouldBe listOf(
      Person(1, "Joe", "Bloggs", 111)
    )
  }

  "Person IN (names) - empty" {
    val sql = Sql("SELECT id, firstName, lastName, age FROM Person WHERE firstName IN ${Params.empty()}").queryOf<Person>()
    sql.sql shouldBe "SELECT id, firstName, lastName, age FROM Person WHERE firstName IN (null)"
    ctx.run(sql) shouldBe listOf()
  }

  "Person IN (names) - empty list" {
    val names: List<String> = emptyList()
    ctx.run(Sql("SELECT id, firstName, lastName, age FROM Person WHERE firstName IN ${Params.list(names)}").queryOf<Person>()) shouldBe listOf()
  }
})