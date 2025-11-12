package io.exoquery.r2dbc.sqlserver

import io.exoquery.controller.runActions
import io.exoquery.controller.runOn
import io.exoquery.controller.r2dbc.R2dbcController
import io.exoquery.controller.r2dbc.R2dbcControllers
import io.exoquery.r2dbc.TestDatabasesR2dbc
import io.exoquery.sql.Param
import io.exoquery.sql.Sql
import io.exoquery.sql.encodingdata.EncodingTestEntity
import io.exoquery.sql.encodingdata.insert
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable

class InjectionSpec: FreeSpec({

  val cf = TestDatabasesR2dbc.sqlServer
  val ctx: R2dbcController by lazy { R2dbcControllers.SqlServer(connectionFactory = cf) }

  suspend fun runActions(actions: String) = ctx.runActions(actions)

  beforeEach {
    runActions(
      """
      TRUNCATE TABLE Person; DBCC CHECKIDENT ('Person', RESEED, 1);
      """
    )
    // Insert a single person row (id should be 1 after reseed)
    runActions("INSERT INTO Person (firstName, lastName, age) VALUES ('Joe', 'Blogs', 123)")
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
