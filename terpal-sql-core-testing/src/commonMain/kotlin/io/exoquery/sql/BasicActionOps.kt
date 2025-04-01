package io.exoquery.sql

import io.exoquery.controller.ControllerTransactional
import io.exoquery.controller.runActions
import io.exoquery.controller.runOn
import io.exoquery.sql.encodingdata.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

class BasicActionOps<Session, Stmt>(
  val ctx: ControllerTransactional<Session, Stmt>
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
  val jim = Person(2, "Jim", "Roogs", 222)

  fun BasicInsert()  = runBlocking {
    Sql("INSERT INTO Person (id, firstName, lastName, age) VALUES (${joe.id}, ${joe.firstName}, ${joe.lastName}, ${joe.age})").action().runOn(ctx);
    Sql("INSERT INTO Person (id, firstName, lastName, age) VALUES (${jim.id}, ${jim.firstName}, ${jim.lastName}, ${jim.age})").action().runOn(ctx);
    Sql("SELECT id, firstName, lastName, age FROM Person").queryOf<Person>().runOn(ctx) shouldBe listOf(joe, jim)
  }

  fun InsertReturning()  = runBlocking {
    val id1 = Sql("INSERT INTO Person (firstName, lastName, age) VALUES (${joe.firstName}, ${joe.lastName}, ${joe.age}) RETURNING id").actionReturning<Long>().runOn(ctx);
    val id2 = Sql("INSERT INTO Person (firstName, lastName, age) VALUES (${jim.firstName}, ${jim.lastName}, ${jim.age}) RETURNING id").actionReturning<Long>().runOn(ctx);
    id1 shouldBe 1
    id2 shouldBe 2
    Sql("SELECT id, firstName, lastName, age FROM Person").queryOf<Person>().runOn(ctx) shouldBe listOf(joe, jim)
  }

  fun InsertReturningRecord()  = runBlocking {
    val person1 = Sql("INSERT INTO Person (firstName, lastName, age) VALUES (${joe.firstName}, ${joe.lastName}, ${joe.age}) RETURNING id, firstName, lastName, age").actionReturning<Person>().runOn(ctx)
    val person2 = Sql("INSERT INTO Person (firstName, lastName, age) VALUES (${jim.firstName}, ${jim.lastName}, ${jim.age}) RETURNING id, firstName, lastName, age").actionReturning<Person>().runOn(ctx)
    person1 shouldBe joe
    person2 shouldBe jim
    Sql("SELECT id, firstName, lastName, age FROM Person").queryOf<Person>().runOn(ctx) shouldBe listOf(joe, jim)
  }

  fun InsertReturningId()  = runBlocking {
    val person1 = Sql("INSERT INTO Person (firstName, lastName, age) VALUES (${joe.firstName}, ${joe.lastName}, ${joe.age})").actionReturningId().runOn(ctx)
    val person2 = Sql("INSERT INTO Person (firstName, lastName, age) VALUES (${jim.firstName}, ${jim.lastName}, ${jim.age})").actionReturningId().runOn(ctx)
    person1 shouldBe 1
    person2 shouldBe 2
    Sql("SELECT id, firstName, lastName, age FROM Person").queryOf<Person>().runOn(ctx) shouldBe listOf(joe, jim)
  }
}
