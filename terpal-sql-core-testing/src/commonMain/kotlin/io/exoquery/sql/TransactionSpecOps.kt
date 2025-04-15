package io.exoquery.sql

import io.exoquery.controller.ControllerTransactional
import io.exoquery.controller.runActions
import io.exoquery.controller.runOn
import io.exoquery.controller.transaction
import io.exoquery.sql.encodingdata.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

class TransactionSpecOps<Session, Stmt, ExecutionOpts>(
  val ctx: ControllerTransactional<Session, Stmt, ExecutionOpts>,
) {

  fun clearTables(): Unit = runBlocking {
    ctx.runActions(
      """
      DELETE FROM Person;
      DELETE FROM Address;
      """
    )
  }

  @Serializable
  data class Person(val id: Int, val firstName: String, val lastName: String, val age: Int)

  val joe = Person(1, "Joe", "Bloggs", 111)
  val jack = Person(2, "Jack", "Roogs", 222)

  // Note the string elements ${...} should not have quotes around them or else they are interpreted as literals
  fun insert(p: Person) =
    Sql("INSERT INTO Person (id, firstName, lastName, age) VALUES (${p.id}, ${p.firstName}, ${p.lastName}, ${p.age})").action()

  fun select() = Sql("SELECT id, firstName, lastName, age FROM Person").queryOf<Person>()

  fun success() = runBlocking {
    ctx.transaction {
      insert(joe).run()
    }
    select().runOn(ctx) shouldBe listOf(joe)
  }

  fun failure() = runBlocking {
    insert(joe).runOn(ctx)
    shouldThrow<IllegalStateException> {
      ctx.transaction {
        insert(jack).run()
        throw IllegalStateException()
      }
    }
    select().runOn(ctx) shouldBe listOf(joe)
  }

  fun nested() = runBlocking {
    ctx.transaction {
      ctx.transaction {
        insert(joe).run()
      }
    }
    select().runOn(ctx) shouldBe listOf(joe)
  }
}
