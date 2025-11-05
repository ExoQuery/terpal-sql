package io.exoquery.sql.postgres

import io.exoquery.controller.runActions
import io.exoquery.controller.runOn
import io.exoquery.controller.r2dbc.R2dbcController
import io.exoquery.sql.Param
import io.exoquery.sql.Sql
import io.exoquery.sql.TestDatabasesR2dbc
import io.exoquery.sql.encodingdata.EncodingTestEntity
import io.exoquery.sql.encodingdata.insert
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable

class InjectionSpec: FreeSpec({
  val ep = TestDatabasesR2dbc.embeddedPostgres
  val cf = TestDatabasesR2dbc.postgres
  val ctx: R2dbcController by lazy { R2dbcController(connectionFactory = cf) }

  suspend fun runActions(actions: String) = ctx.runActions(actions)

  beforeSpec { SchemaInitR2dbc.ensureApplied(ctx) }
  afterSpec { try { ep.close() } catch (_: Throwable) {} }

  beforeEach {
    runActions("DELETE FROM Person")
    runActions("INSERT INTO Person (id, firstName, lastName, age) VALUES (1, 'Joe', 'Blogs', 123)")
  }

  @Serializable
  data class Person(val id: Int, val firstName: String, val lastName: String, val age: Int)

  "escapes column meant to be an injection attack" {
    insert(EncodingTestEntity.regular).runOn(ctx)
    val name = "'Joe'; DROP TABLE Person;"
    Sql("SELECT * FROM Person WHERE firstName = ${Param.withSer(name)}").queryOf<Person>().runOn(ctx) shouldBe listOf()

    // verify table still exists and is intact
    Sql("SELECT * FROM Person").queryOf<Person>().runOn(ctx) shouldBe listOf(Person(1, "Joe", "Blogs", 123))
  }
})
