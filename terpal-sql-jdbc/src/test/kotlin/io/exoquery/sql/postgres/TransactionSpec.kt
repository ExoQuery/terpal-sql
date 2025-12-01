package io.exoquery.sql.postgres

import io.exoquery.sql.TestDatabases
import io.exoquery.controller.jdbc.JdbcControllers
import io.exoquery.controller.runOn
import io.exoquery.controller.transaction
import io.exoquery.sql.Sql
import io.exoquery.sql.run
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class TransactionSpec: FreeSpec({
  val ds = TestDatabases.postgres
  val ctx by lazy { JdbcControllers.Postgres(ds) }
  beforeEach {
    ds.run(
      """
      DELETE FROM Person;
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
      Sql("INSERT INTO Person (id, firstName, lastName, age) VALUES (${p.id}, ${p.firstName}, ${p.lastName}, ${p.age})").action()


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
    "child coroutines share the same transaction connection" {
      val ann = Person(3, "Ann", "Smith", 333)
      val bob = Person(4, "Bob", "Jones", 444)

      ctx.transaction {
        coroutineScope {
          launch { insert(ann).run() }
          launch { insert(bob).run() }
        }
        // Both launches used the same connection/transaction
      }

      select().runOn(ctx).toSet() shouldBe setOf(ann, bob)
    }
  }
})
