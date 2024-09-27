package io.exoquery.sql.postgres

import io.exoquery.sql.*
import io.exoquery.sql.encodingdata.EncodingTestEntity
import io.exoquery.sql.Sql
import io.exoquery.sql.encodingdata.insert
import io.exoquery.sql.jdbc.TerpalDriver
import io.exoquery.sql.runOn
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable

class InjectionSpec: FreeSpec({
  val ds = TestDatabases.postgres
  val ctx by lazy {
    TerpalDriver.Postgres(ds)
  }

  beforeEach {
    ds.run("DELETE FROM Person")
    ds.run("INSERT INTO Person (id, firstName, lastName, age) VALUES (1, 'Joe', 'Blogs', 123)")
  }

  @Serializable
  data class Person(val id: Int, val firstName: String, val lastName: String, val age: Int)

  "escapes column meant to be an injection attack" {
    ctx.run(insert(EncodingTestEntity.regular))
    val name = "'Joe'; DROP TABLE Person;"
    Sql("SELECT * FROM Person WHERE firstName = ${name}").queryOf<Person>().runOn(ctx) shouldBe listOf()

    // This would cause the relation to be dropped
    //val res = ds.run("SELECT * FROM Person WHERE firstName = ${name}")

    Sql("SELECT * FROM Person").queryOf<Person>().runOn(ctx) shouldBe listOf(Person(1, "Joe", "Blogs", 123))
  }
})
