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
    ds.run("DELETE FROM JsonbExample2")
    ds.run("DELETE FROM JsonbExample3")
    ds.run("DELETE FROM JsonExample")
  }

  "SqlJsonValue annotation works on" - {
    "inner data class" - {
      @SqlJsonValue
      @Serializable
      data class MyPerson(val name: String, val age: Int)

      @Serializable
      data class Example(val id: Int, val value: MyPerson)

      val je = Example(1, MyPerson("Alice", 30))

      "should encode in jsonb and decode" {
        Sql("INSERT INTO JsonbExample (id, value) VALUES (1, ${Param.withSer(je.value)})").action().runOn(ctx)
        Sql("SELECT id, value FROM JsonbExample").queryOf<Example>().runOn(ctx) shouldBe listOf(je)
      }

      "should encode in jsonb and decode as atom" {
        Sql("INSERT INTO JsonbExample (id, value) VALUES (1, ${Param.withSer(je.value)})").action().runOn(ctx)
        Sql("SELECT value FROM JsonbExample").queryOf<MyPerson>().runOn(ctx) shouldBe listOf(je.value)
      }

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
    "inner data class" - {
      @Serializable
      data class MyPerson(val name: String, val age: Int)

      @Serializable
      data class JsonbExample(val id: Int, val jsonValue: JsonValue<MyPerson>)

      "field value" {
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
    "complex data classes" - {
      @Serializable
      data class MyPerson(val name: String, val age: Int)

      @Serializable
      data class JsonbExample(val id: Int, val jsonValue: JsonValue<List<MyPerson>>)

      val people = listOf(MyPerson("Joe", 30), MyPerson("Jack", 31))
      val je = JsonbExample(1, JsonValue(people))

      "field value" {
        Sql("INSERT INTO JsonbExample (id, value) VALUES (1, ${Param.withSer(je.jsonValue)})").action().runOn(ctx)
        Sql("SELECT id, value FROM JsonbExample").queryOf<JsonbExample>().runOn(ctx) shouldBe listOf(je)
      }

      "leaf value" {
        val je1 = JsonbExample(1, JsonValue(people))
        Sql("INSERT INTO JsonbExample (id, value) VALUES (1, ${Param.withSer(je.jsonValue)})").action().runOn(ctx)
        Sql("SELECT value FROM JsonbExample").queryOf<JsonValue<List<MyPerson>>>().runOn(ctx) shouldBe listOf(je.jsonValue)
      }
    }
  }
  "multiple complex data classes" - {
    @Serializable
    data class MyPerson(val name: String, val age: Int)

    @Serializable
    data class MyJob(val job: String, val salary: Long)

    @Serializable
    data class JsonbExample2(val id: Int, val jsonValue1: JsonValue<List<MyPerson>>, val jsonValue2: JsonValue<List<MyJob>>)

    val people = listOf(MyPerson("Joe", 30), MyPerson("Jack", 31))
    val jobs = listOf(MyJob("job1", 100), MyJob("job2", 200))
    val je = JsonbExample2(1, JsonValue(people), JsonValue(jobs))

    "field value" {
      Sql("INSERT INTO JsonbExample2 (id, value1, value2) VALUES (1, ${Param.withSer(je.jsonValue1)}, ${Param.withSer(je.jsonValue2)})").action().runOn(ctx)
      Sql("SELECT id, value1, value2  FROM JsonbExample2").queryOf<JsonbExample2>().runOn(ctx) shouldBe listOf(je)
    }
  }
  "complex data classes before primitive column" - {
    @Serializable
    data class MyPerson(val name: String, val age: Int)

    @Serializable
    data class JsonbExample3(val id: Int, val jsonValue1: JsonValue<List<MyPerson>>, val sample: Int)

    val people = listOf(MyPerson("Joe", 30), MyPerson("Jack", 31))
    val je = JsonbExample3(1, JsonValue(people), 100)

    "field value" {
      Sql("INSERT INTO JsonbExample3 (id, value, sample) VALUES (1, ${Param.withSer(je.jsonValue1)}, 100)").action().runOn(ctx)
      Sql("SELECT id, value, sample  FROM JsonbExample3").queryOf<JsonbExample3>().runOn(ctx) shouldBe listOf(je)
    }
  }

})