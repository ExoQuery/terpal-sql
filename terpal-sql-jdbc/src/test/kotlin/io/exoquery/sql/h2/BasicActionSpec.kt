package io.exoquery.sql.h2

import io.exoquery.sql.TestDatabases
import io.exoquery.controller.jdbc.JdbcControllers
import io.exoquery.sql.Sql
import io.exoquery.controller.runOn
import io.exoquery.sql.run
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable

class BasicActionSpec : FreeSpec({

  val ds = TestDatabases.h2
  val ctx by lazy { JdbcControllers.H2(ds)  }

  beforeEach {
    ds.run(
      """
      TRUNCATE TABLE Person; 
      ALTER TABLE Person ALTER COLUMN id RESTART WITH 1;
      TRUNCATE TABLE Address;
      """
    )
  }

  @Serializable
  data class Person(val id: Int, val firstName: String, val lastName: String, val age: Int)

  val joe = Person(1, "Joe", "Bloggs", 111)
  val jim = Person(2, "Jim", "Roogs", 222)

  "Basic Insert" {
    Sql("INSERT INTO Person (id, firstName, lastName, age) VALUES (${joe.id}, ${joe.firstName}, ${joe.lastName}, ${joe.age})").action().runOn(ctx);
    Sql("INSERT INTO Person (id, firstName, lastName, age) VALUES (${jim.id}, ${jim.firstName}, ${jim.lastName}, ${jim.age})").action().runOn(ctx);
    Sql("SELECT id, firstName, lastName, age FROM Person").queryOf<Person>().runOn(ctx) shouldBe listOf(joe, jim)
  }

  "Insert Returning" {
    val id1 = Sql("INSERT INTO Person (firstName, lastName, age) VALUES (${joe.firstName}, ${joe.lastName}, ${joe.age})").actionReturning<Int>().runOn(ctx);
    val id2 = Sql("INSERT INTO Person (firstName, lastName, age) VALUES (${jim.firstName}, ${jim.lastName}, ${jim.age})").actionReturning<Int>().runOn(ctx);
    id1 shouldBe 1
    id2 shouldBe 2
    Sql("SELECT id, firstName, lastName, age FROM Person").queryOf<Person>().runOn(ctx) shouldBe listOf(joe, jim)
  }

  "Insert Returning Ids" {
    val id1 = Sql("INSERT INTO Person (firstName, lastName, age) VALUES (${joe.firstName}, ${joe.lastName}, ${joe.age})").actionReturningId().runOn(ctx);
    val id2 = Sql("INSERT INTO Person (firstName, lastName, age) VALUES (${jim.firstName}, ${jim.lastName}, ${jim.age})").actionReturningId().runOn(ctx);
    id1 shouldBe 1
    id2 shouldBe 2
    Sql("SELECT id, firstName, lastName, age FROM Person").queryOf<Person>().runOn(ctx) shouldBe listOf(joe, jim)
  }

  "Insert Returning Ids - explicit" {
    val id1 = Sql("INSERT INTO Person (firstName, lastName, age) VALUES (${joe.firstName}, ${joe.lastName}, ${joe.age})").actionReturningId("id").runOn(ctx);
    val id2 = Sql("INSERT INTO Person (firstName, lastName, age) VALUES (${jim.firstName}, ${jim.lastName}, ${jim.age})").actionReturningId("id").runOn(ctx);
    id1 shouldBe 1
    id2 shouldBe 2
    Sql("SELECT id, firstName, lastName, age FROM Person").queryOf<Person>().runOn(ctx) shouldBe listOf(joe, jim)
  }
})
