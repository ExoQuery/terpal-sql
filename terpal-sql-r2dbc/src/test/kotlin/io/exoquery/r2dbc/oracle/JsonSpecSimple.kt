package io.exoquery.r2dbc.oracle

import io.exoquery.controller.SqlJsonValue
import io.exoquery.controller.TerpalSqlUnsafe
import io.exoquery.controller.r2dbc.R2dbcController
import io.exoquery.controller.r2dbc.R2dbcControllers
import io.exoquery.controller.runActionsUnsafe
import io.exoquery.controller.runOn
import io.exoquery.r2dbc.TestDatabasesR2dbc
import io.exoquery.sql.Param
import io.exoquery.sql.Sql
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable

class JsonSpec: FreeSpec({
  val cf = TestDatabasesR2dbc.oracle
  val ctx: R2dbcController by lazy { R2dbcControllers.Oracle(connectionFactory = cf) }

  @OptIn(TerpalSqlUnsafe::class)
  suspend fun runActions(actions: String) = ctx.runActionsUnsafe(actions)

  beforeEach {
    runActions("DELETE FROM JsonExample")
  }

  "SqlJsonValue annotation works on" - {
    "inner data class" - {
      @SqlJsonValue
      @Serializable
      data class MyPerson(val name: String, val age: Int)

      @Serializable
      data class Example(val id: Int, val value: MyPerson)

      val je = Example(1, MyPerson("Alice", 30))

      "should encode in json (with explicit serializer) and decode" {
        Sql("INSERT INTO JsonExample (id, value) VALUES (1, ${Param.withSer(je.value, MyPerson.serializer())})").action().runOn(ctx)
        Sql("SELECT id, value FROM JsonExample").queryOf<Example>().runOn(ctx) shouldBe listOf(je)
      }
    }

    "annotated field" {
      @Serializable
      data class MyPerson(val name: String, val age: Int)

      @Serializable
      data class Example(val id: Int, @SqlJsonValue val value: MyPerson)

      val je = Example(1, MyPerson("Joe", 123))
      Sql("""INSERT INTO JsonExample (id, value) VALUES (1, '{"name":"Joe", "age":123}')""").action().runOn(ctx)
      val customers = Sql("SELECT id, value FROM JsonExample").queryOf<Example>().runOn(ctx)
      customers shouldBe listOf(je)
    }
  }

})
