package io.exoquery.sql.postgres

import io.exoquery.sql.*
import io.exoquery.sql.jdbc.Sql
import io.exoquery.sql.EncodingSpecData.insert
import io.exoquery.sql.jdbc.JdbcEncodingBasic.Companion.StringEncoder
import io.exoquery.sql.jdbc.TerpalContext
import io.exoquery.sql.jdbc.runOn
import io.kotest.core.spec.style.FreeSpec
import java.time.ZoneId
import io.exoquery.sql.EncodingSpecData.TimeEntity
import io.exoquery.sql.EncodingSpecData.insertBatch
import io.exoquery.sql.examples.Simple_SqlServer
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable

class InjectionSpec: FreeSpec({
  val ds = TestDatabases.postgres
  val ctx by lazy {
    TerpalContext.Postgres(ds)
  }

  beforeEach {
    ds.run("DELETE FROM Person")
    ds.run("INSERT INTO Person (id, firstName, lastName, age) VALUES (1, 'Joe', 'Blogs', 123)")
  }

  @Serializable
  data class Person(val id: Int, val firstName: String, val lastName: String, val age: Int)

  "escapes column meant to be an injection attack" {
    ctx.run(insert(EncodingSpecData.regularEntity))
    val name = "'Joe'; DROP TABLE Person;"
    Sql("SELECT * FROM Person WHERE firstName = ${name}").queryOf<Person>().runOn(ctx) shouldBe listOf()

    // This would cause the relation to be dropped
    //val res = ds.run("SELECT * FROM Person WHERE firstName = ${name}")

    Sql("SELECT * FROM Person").queryOf<Person>().runOn(ctx) shouldBe listOf(Person(1, "Joe", "Blogs", 123))
  }
})
