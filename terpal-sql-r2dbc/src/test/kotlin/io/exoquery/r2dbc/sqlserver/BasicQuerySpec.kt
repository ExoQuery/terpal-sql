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

class BasicQuerySpec : FreeSpec({

  val cf = TestDatabasesR2dbc.sqlServer
  val ctx: R2dbcController by lazy { R2dbcControllers.SqlServer(connectionFactory = cf) }

  suspend fun runActions(actions: String) = ctx.runActions(actions)

  beforeSpec {
    runActions(
      """
      TRUNCATE TABLE Person; DBCC CHECKIDENT ('Person', RESEED, 1);
      DELETE FROM Address;
      INSERT INTO Person (firstName, lastName, age) VALUES ('Joe', 'Bloggs', 111);
      INSERT INTO Person (firstName, lastName, age) VALUES ('Jim', 'Roogs', 222);
      INSERT INTO Address (ownerId, street, zip) VALUES (1, '123 Main St', '12345');
      """.trimIndent()
    )
  }

  "SELECT Person - simple" {
    @Serializable
    data class Person(val id: Int, val firstName: String, val lastName: String, val age: Int)

    Sql("SELECT id, firstName, lastName, age FROM Person").queryOf<Person>().runOn(ctx) shouldBe listOf(
      Person(1, "Joe", "Bloggs", 111),
      Person(2, "Jim", "Roogs", 222)
    )
  }

  "joins" - {
    @Serializable
    data class Person(val id: Int, val firstName: String, val lastName: String, val age: Int)
    @Serializable
    data class Address(val ownerId: Int, val street: String, val zip: Int)

    "SELECT Person, Address - join" {
      Sql("SELECT p.id, p.firstName, p.lastName, p.age, a.ownerId, a.street, a.zip FROM Person p JOIN Address a ON p.id = a.ownerId").queryOf<Pair<Person, Address>>().runOn(ctx) shouldBe listOf(
        Person(1, "Joe", "Bloggs", 111) to Address(1, "123 Main St", 12345)
      )
    }

    "SELECT Person, Address - leftJoin + null" {
      Sql("SELECT p.id, p.firstName, p.lastName, p.age, a.ownerId, a.street, a.zip FROM Person p LEFT JOIN Address a ON p.id = a.ownerId").queryOf<Pair<Person, Address?>>().runOn(ctx) shouldBe listOf(
        Person(1, "Joe", "Bloggs", 111) to Address(1, "123 Main St", 12345),
        Person(2, "Jim", "Roogs", 222) to null
      )
    }

    "SELECT Person, Address - leftJoin + null (Triple(NN,null,null))" {
      Sql("SELECT p.id, p.firstName, p.lastName, p.age, a.ownerId, a.street, a.zip, aa.ownerId, aa.street, aa.zip FROM Person p LEFT JOIN Address a ON p.id = a.ownerId LEFT JOIN Address aa ON p.id = aa.ownerId").queryOf<Triple<Person, Address?, Address?>>().runOn(ctx) shouldBe listOf(
        Triple(Person(1, "Joe", "Bloggs", 111), Address(1, "123 Main St", 12345), Address(1, "123 Main St", 12345)),
        Triple(Person(2, "Jim", "Roogs", 222), null, null)
      )
    }

    // Test advancement of child decoder indices when nulls
    "SELECT Person, Address - leftJoin + null (Triple(NN,null,NN))" {
      Sql("SELECT p.id, p.firstName, p.lastName, p.age, a.ownerId, a.street, a.zip, aa.ownerId, aa.street, aa.zip FROM Person p LEFT JOIN Address a ON p.id = a.ownerId LEFT JOIN Address aa ON 1 = aa.ownerId").queryOf<Triple<Person, Address?, Address?>>().runOn(ctx) shouldBe listOf(
        Triple(Person(1, "Joe", "Bloggs", 111), Address(1, "123 Main St", 12345), Address(1, "123 Main St", 12345)),
        Triple(Person(2, "Jim", "Roogs", 222), null, Address(1, "123 Main St", 12345))
      )
    }

    @Serializable
    data class CustomRow1(val Person: Person, val Address: Address)
    @Serializable
    data class CustomRow2(val Person: Person, val Address: Address?)

    "SELECT Person, Address - join - custom row" {
      Sql("SELECT p.id, p.firstName, p.lastName, p.age, a.ownerId, a.street, a.zip FROM Person p JOIN Address a ON p.id = a.ownerId").queryOf<CustomRow1>().runOn(ctx) shouldBe listOf(
        CustomRow1(Person(1, "Joe", "Bloggs", 111), Address(1, "123 Main St", 12345))
      )
    }

    "SELECT Person, Address - leftJoin + null - custom row" {
      Sql("SELECT p.id, p.firstName, p.lastName, p.age, a.ownerId, a.street, a.zip FROM Person p LEFT JOIN Address a ON p.id = a.ownerId").queryOf<CustomRow2>().runOn(ctx) shouldBe listOf(
        CustomRow2(Person(1, "Joe", "Bloggs", 111), Address(1, "123 Main St", 12345)),
        CustomRow2(Person(2, "Jim", "Roogs", 222), null)
      )
    }
  }

  "joins + null complex" - {
    @Serializable
    data class Person(val id: Int, val firstName: String?, val lastName: String, val age: Int)
    @Serializable
    data class Address(val ownerId: Int?, val street: String, val zip: Int)

    "SELECT Person, Address - join" {
      Sql("SELECT p.id, null as firstName, p.lastName, p.age, null as ownerId, a.street, a.zip FROM Person p JOIN Address a ON p.id = a.ownerId").queryOf<Pair<Person, Address>>().runOn(ctx) shouldBe listOf(
        Person(1, null, "Bloggs", 111) to Address(null, "123 Main St", 12345)
      )
    }

    "SELECT Person, Address - leftJoin + null" {
      Sql("SELECT p.id, null as firstName, p.lastName, p.age, null as ownerId, a.street, a.zip FROM Person p LEFT JOIN Address a ON p.id = a.ownerId").queryOf<Pair<Person, Address?>>().runOn(ctx) shouldBe listOf(
        Person(1, null, "Bloggs", 111) to Address(null, "123 Main St", 12345),
        Person(2, null, "Roogs", 222) to null
      )
    }
  }

  "SELECT Person - nested" {
    @Serializable
    data class Name(val firstName: String, val lastName: String)
    @Serializable
    data class Person(val id: Int, val name: Name, val age: Int)

    Sql("SELECT id, firstName, lastName, age FROM Person").queryOf<Person>().runOn(ctx) shouldBe listOf(
      Person(1, Name("Joe", "Bloggs"), 111),
      Person(2, Name("Jim", "Roogs"), 222)
    )
  }

  "SELECT Person - nested with join" {
    @Serializable
    data class Name(val firstName: String, val lastName: String)
    @Serializable
    data class Person(val id: Int, val name: Name, val age: Int)
    @Serializable
    data class Address(val street: String, val zip: Int)

    Sql("SELECT p.id, p.firstName, p.lastName, p.age, a.street, a.zip FROM Person p JOIN Address a ON p.id = a.ownerId").queryOf<Pair<Person, Address>>().runOn(ctx) shouldBe listOf(
      Person(1, Name("Joe", "Bloggs"), 111) to Address("123 Main St", 12345)
    )
  }
})
