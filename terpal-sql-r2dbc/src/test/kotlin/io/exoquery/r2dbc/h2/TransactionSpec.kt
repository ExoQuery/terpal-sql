package io.exoquery.r2dbc.h2

import io.exoquery.controller.TerpalSqlUnsafe
import io.exoquery.controller.r2dbc.R2dbcController
import io.exoquery.controller.r2dbc.R2dbcControllers
import io.exoquery.controller.runActionsUnsafe
import io.exoquery.controller.runOn
import io.exoquery.controller.transaction
import io.exoquery.r2dbc.TestDatabasesR2dbc
import io.exoquery.sql.Sql
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable

@OptIn(TerpalSqlUnsafe::class)
class TransactionSpec: FreeSpec({
  val cf = TestDatabasesR2dbc.h2
  val ctx: R2dbcController by lazy { R2dbcControllers.H2(connectionFactory = cf) }
  beforeEach {
    ctx.runActionsUnsafe(
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
  }
})
