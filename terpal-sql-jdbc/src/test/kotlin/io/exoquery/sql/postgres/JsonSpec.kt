package io.exoquery.sql.postgres

import io.exoquery.sql.*
import io.exoquery.sql.jdbc.Sql
import io.exoquery.sql.jdbc.TerpalContext
import io.exoquery.sql.jdbc.runOn
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable

typealias MyPersonJson = @Serializable @SqlJsonValue JsonSpecData.A.MyPerson

object JsonSpecData {
  object A {
    @Serializable
    data class MyPerson(val name: String, val age: Int)

    @Serializable
    data class Example(val id: Int, val value: MyPersonJson)
  }
}

class JsonSpec: FreeSpec({
  val ds = TestDatabases.postgres
  val ctx by lazy {
    TerpalContext.Postgres(ds)
  }

  beforeEach {
    ds.run("DELETE FROM JsonbExample")
    ds.run("DELETE FROM JsonExample")
  }

  "SqlJsonValue annotation works on" - {
    "inner data class" {
      @SqlJsonValue
      @Serializable
      data class MyPerson(val name: String, val age: Int)

      @Serializable
      data class Example(val id: Int, val value: MyPerson)

      val je = Example(1, MyPerson("Alice", 30))
      Sql("INSERT INTO JsonbExample (id, value) VALUES (1, ${Param.withSer(je.value)})").action().runOn(ctx)
      Sql("SELECT id, value FROM JsonbExample").queryOf<Example>().runOn(ctx) shouldBe listOf(je)

      Sql("INSERT INTO JsonExample (id, value) VALUES (1, ${Param.withSer(je.value, MyPerson.serializer())})").action().runOn(ctx)
      Sql("SELECT id, value FROM JsonExample").queryOf<Example>().runOn(ctx) shouldBe listOf(je)
    }

    "annotated field" {
      @Serializable
      data class MyPerson(val name: String, val age: Int)

      @Serializable
      data class Example(val id: Int, @SqlJsonValue val value: MyPerson)

      val je = Example(1, MyPerson("Joe", 123))
      Sql("""INSERT INTO JsonbExample (id, value) VALUES (1, '{"name":"Joe", "age":123}')""").action().runOn(ctx)
      val customers = Sql("SELECT id, value FROM JsonbExample").queryOf<Example>().runOn(ctx)
      customers shouldBe listOf(je)
    }

    "outer typealias - A".config(enabled = false) {
      val je = JsonSpecData.A.Example(1, JsonSpecData.A.MyPerson("Joe", 123))
      Sql("""INSERT INTO JsonbExample (id, value) VALUES (1, '{"name":"Joe", "age":123}')""").action().runOn(ctx)
      val customers = Sql("SELECT id, value FROM JsonbExample").queryOf<JsonSpecData.A.Example>().runOn(ctx)
      customers shouldBe listOf(je)
    }
  }

  "JsonValue object works on" - {
    @Serializable
    data class MyPerson(val name: String, val age: Int)

    @Serializable
    data class JsonbExample(val id: Int, val jsonValue: JsonValue<MyPerson>)

    "inner data class" {
      val je = JsonbExample(1, JsonValue(MyPerson("Alice", 30)))
      Sql("INSERT INTO JsonbExample (id, value) VALUES (1, ${Param.withSer(je.jsonValue)})").action().runOn(ctx)
      Sql("SELECT id, value FROM JsonbExample").queryOf<JsonbExample>().runOn(ctx) shouldBe listOf(je)
    }

    "leaf value" {
      val je = JsonbExample(1, JsonValue(MyPerson("Alice", 30)))
      Sql("INSERT INTO JsonbExample (id, value) VALUES (1, ${Param.withSer(je.jsonValue)})").action().runOn(ctx)
      Sql("SELECT value FROM JsonbExample").queryOf<JsonValue<MyPerson>>().runOn(ctx) shouldBe listOf(je.jsonValue)
    }
  }

})