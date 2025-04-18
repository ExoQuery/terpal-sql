package io.exoquery.sql.android

import io.exoquery.controller.runOn
import io.exoquery.sql.Sql
import io.exoquery.sql.encodingdata.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.BeforeTest


@RunWith(RobolectricTestRunner::class)
class BasicQuerySpec {
  val ctx get() = TestDatabase.ctx

  @BeforeTest
  fun clearTables() {
    ctx.runRaw(
      """
      DELETE FROM Person;
      DELETE FROM Address;
      INSERT INTO Person (id, firstName, lastName, age) VALUES (1, 'Joe', 'Bloggs', 111);
      INSERT INTO Person (id, firstName, lastName, age) VALUES (2, 'Jim', 'Roogs', 222);
      INSERT INTO Address (ownerId, street, zip) VALUES (1, '123 Main St', '12345');
      """
    )
  }

  @Test
  fun `SELECT Person - simple`() = runBlocking {
    @Serializable
    data class Person(val id: Int, val firstName: String, val lastName: String, val age: Int)

    Sql("SELECT id, firstName, lastName, age FROM Person").queryOf<Person>().runOn(ctx) shouldBe listOf(
      Person(1, "Joe", "Bloggs", 111),
      Person(2, "Jim", "Roogs", 222)
    )
  }

  @Test
  fun `SELECT Person - simple with param`() = runBlocking {
    @Serializable
    data class Person(val id: Int, val firstName: String, val lastName: String, val age: Int)

    val joe = "Joe"
    Sql("SELECT id, firstName, lastName, age FROM Person WHERE firstName = ${joe}").queryOf<Person>().runOn(ctx) shouldBe listOf(
      Person(1, "Joe", "Bloggs", 111)
    )
  }

  object Joins {
    @Serializable
    data class Person(val id: Int, val firstName: String, val lastName: String, val age: Int)
    @Serializable
    data class Address(val ownerId: Int, val street: String, val zip: String)

    @Serializable
    data class CustomRow1(val Person: Person, val Address: Address)
    @Serializable
    data class CustomRow2(val Person: Person, val Address: Address?)
  }

  @Test
  fun `joins - SELECT Person Address`() = runBlocking {
    Sql("SELECT p.id, p.firstName, p.lastName, p.age, a.ownerId, a.street, a.zip FROM Person p JOIN Address a ON p.id = a.ownerId").queryOf<kotlin.Pair<Joins.Person, Joins.Address>>().runOn(ctx) shouldBe kotlin.collections.listOf(
      Joins.Person(1, "Joe", "Bloggs", 111) to Joins.Address(1, "123 Main St", "12345")
    )
  }

  @Test
  fun `joins - SELECT Person Address leftJoin + null`() = runBlocking {
    Sql("SELECT p.id, p.firstName, p.lastName, p.age, a.ownerId, a.street, a.zip FROM Person p LEFT JOIN Address a ON p.id = a.ownerId").queryOf<Pair<Joins.Person, Joins.Address?>>().runOn(ctx) shouldBe listOf(
      Joins.Person(1, "Joe", "Bloggs", 111) to Joins.Address(1, "123 Main St", "12345"),
      Joins.Person(2, "Jim", "Roogs", 222) to null
    )
  }

  @Test
  fun `joins - SELECT Person Address join - custom row`() = runBlocking {
    Sql("SELECT p.id, p.firstName, p.lastName, p.age, a.ownerId, a.street, a.zip FROM Person p JOIN Address a ON p.id = a.ownerId").queryOf<Joins.CustomRow1>().runOn(ctx) shouldBe listOf(
      Joins.CustomRow1(Joins.Person(1, "Joe", "Bloggs", 111), Joins.Address(1, "123 Main St", "12345"))
    )
  }

  @Test
  fun `joins - SELECT Person Address leftJoin + null - custom row`() = runBlocking {
    Sql("SELECT p.id, p.firstName, p.lastName, p.age, a.ownerId, a.street, a.zip FROM Person p LEFT JOIN Address a ON p.id = a.ownerId").queryOf<Joins.CustomRow2>().runOn(ctx) shouldBe listOf(
      Joins.CustomRow2(Joins.Person(1, "Joe", "Bloggs", 111), Joins.Address(1, "123 Main St", "12345")),
      Joins.CustomRow2(Joins.Person(2, "Jim", "Roogs", 222), null)
    )
  }

  object JoinsNullComplex {
    @Serializable
    data class Person(val id: Int, val firstName: String?, val lastName: String, val age: Int)
    @Serializable
    data class Address(val ownerId: Int?, val street: String, val zip: String)
  }

  @Test
  fun `joins + null complex - SELECT Person Address join`() = runBlocking {
    Sql("SELECT p.id, null as firstName, p.lastName, p.age, null as ownerId, a.street, a.zip FROM Person p JOIN Address a ON p.id = a.ownerId").queryOf<Pair<JoinsNullComplex.Person, JoinsNullComplex.Address>>().runOn(ctx) shouldBe listOf(
      JoinsNullComplex.Person(1, null, "Bloggs", 111) to JoinsNullComplex.Address(null, "123 Main St", "12345")
    )
  }

  @Test
  fun `joins + null complex - SELECT Person Address leftJoin + null`() = runBlocking {
    Sql("SELECT p.id, null as firstName, p.lastName, p.age, null as ownerId, a.street, a.zip FROM Person p LEFT JOIN Address a ON p.id = a.ownerId").queryOf<Pair<JoinsNullComplex.Person, JoinsNullComplex.Address?>>().runOn(ctx) shouldBe listOf(
      JoinsNullComplex.Person(1, null, "Bloggs", 111) to JoinsNullComplex.Address(null, "123 Main St", "12345"),
      JoinsNullComplex.Person(2, null, "Roogs", 222) to null
    )
  }

  @Test
  fun `SELECT Person - nested`() = runBlocking {
    @Serializable
    data class Name(val firstName: String, val lastName: String)
    @Serializable
    data class Person(val id: Int, val name: Name, val age: Int)

    Sql("SELECT id, firstName, lastName, age FROM Person").queryOf<Person>().runOn(ctx) shouldBe listOf(
      Person(1, Name("Joe", "Bloggs"), 111),
      Person(2, Name("Jim", "Roogs"), 222)
    )
  }

  @Test
  fun `SELECT Person - nested with join`() = runBlocking {
    @Serializable
    data class Name(val firstName: String, val lastName: String)
    @Serializable
    data class Person(val id: Int, val name: Name, val age: Int)
    @Serializable
    data class Address(val ownerId: Int, val street: String, val zip: String)

    Sql("SELECT p.id, p.firstName, p.lastName, p.age, a.ownerId, a.street, a.zip FROM Person p JOIN Address a ON p.id = a.ownerId").queryOf<Pair<Person, Address>>().runOn(ctx) shouldBe listOf(
      Person(1, Name("Joe", "Bloggs"), 111) to Address(1, "123 Main St", "12345")
    )
  }
}
