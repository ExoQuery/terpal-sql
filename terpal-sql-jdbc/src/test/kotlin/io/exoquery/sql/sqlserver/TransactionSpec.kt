package io.exoquery.sql.sqlserver

import io.exoquery.sql.TestDatabases
import io.exoquery.controller.jdbc.DatabaseController
import io.exoquery.controller.runOn
import io.exoquery.sql.Sql
import io.exoquery.sql.run
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable

class TransactionSpec: FreeSpec({
  val ds = TestDatabases.sqlServer
  val ctx by lazy { DatabaseController.SqlServer(ds) }
  beforeEach {
    ds.run(
      """
      TRUNCATE TABLE Person; DBCC CHECKIDENT ('Person', RESEED, 1);
      DELETE FROM Address;
      """
    )
  }

  @Serializable
  data class Person(val id: Int, val firstName: String, val lastName: String, val age: Int)

  "provides transaction support" - {
    val joe = Person(1, "Joe", "Bloggs", 111)
    val jack = Person(2, "Jack", "Roogs", 222)

    // Note the string elements ${...} should not have quotes around them or else they are interpreted as literals
    fun insert(p: Person) =
      Sql("INSERT INTO Person (firstName, lastName, age) VALUES (${p.firstName}, ${p.lastName}, ${p.age})").action()


    fun select() = Sql("SELECT id, firstName, lastName, age FROM Person").queryOf<Person>()

    "success" {
      ctx.transaction {
        insert(joe).run()
      }
      select().runOn(ctx) shouldBe listOf(joe)
    }
    "failure" {
      insert(joe).runOn(ctx)
      shouldThrow<IllegalStateException> {
        ctx.transaction {
          insert(jack).run()
          throw IllegalStateException()
        }
      }
      select().runOn(ctx) shouldBe listOf(joe)
    }
    "nested" {
      ctx.transaction {
        ctx.transaction {
          insert(joe).run()
        }
      }
      select().runOn(ctx) shouldBe listOf(joe)
    }
  }
})