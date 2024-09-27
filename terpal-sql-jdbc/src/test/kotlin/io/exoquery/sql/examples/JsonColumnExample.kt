package io.exoquery.sql.examples

import io.exoquery.sql.JsonValue
import io.exoquery.sql.Param
import io.exoquery.sql.SqlJsonValue
import io.exoquery.sql.Sql
import io.exoquery.sql.jdbc.TerpalDriver
import io.exoquery.sql.runOn
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.serialization.Serializable

object JsonColumnExample1 {

  @SqlJsonValue
  @Serializable
  data class MyPerson(val name: String, val age: Int)

  @Serializable
  data class JsonbExample(val id: Int, val jsonbValue: MyPerson)

  suspend fun main() {
    val postgres = EmbeddedPostgres.start()
    postgres.run("CREATE TABLE JsonbExample(id SERIAL PRIMARY KEY, jsonbValue JSONB)")
    val ctx = TerpalDriver.Postgres(postgres.postgresDatabase)
    val je = JsonbExample(1, MyPerson("Alice", 30))
    Sql("INSERT INTO JsonbExample (id, jsonbValue) VALUES (1, ${Param.withSer(je.jsonbValue, MyPerson.serializer())})").action().runOn(ctx)
    //val customers = Sql("SELECT id, jsonbValue FROM JsonbExample").queryOf<JsonbExample>().runOn(ctx)
    //println(customers)

    val people = Sql("SELECT jsonbValue FROM JsonbExample").queryOf<MyPerson>().runOn(ctx)
    println(people)
  }
}

object JsonColumnExample2 {

  @Serializable
  data class MyPerson(val name: String, val age: Int)

  @Serializable
  data class JsonbExample(val id: Int, @SqlJsonValue val jsonbValue: MyPerson)

  suspend fun main() {
    val postgres = EmbeddedPostgres.start()
    postgres.run("CREATE TABLE JsonbExample(id SERIAL PRIMARY KEY, jsonbValue JSONB)")
    val ctx = TerpalDriver.Postgres(postgres.postgresDatabase)
    val je = JsonbExample(1, MyPerson("Alice", 30))
    //Sql("INSERT INTO JsonbExample (id, jsonbValue) VALUES (1, ${Param.withSer(je.jsonbValue, MyPerson.serializer())})").action().runOn(ctx)
    Sql("""INSERT INTO JsonbExample (id, jsonbValue) VALUES (1, '{"name":"Joe", "age":123}')""").action().runOn(ctx)
    val customers = Sql("SELECT id, jsonbValue FROM JsonbExample").queryOf<JsonbExample>().runOn(ctx)
    println(customers)
  }
}

object JsonColumnExample4 {

  @Serializable
  data class MyPerson(val name: String, val age: Int)

  @Serializable
  data class JsonbExample(val id: Int, val jsonbValue: JsonValue<MyPerson>)

  suspend fun main() {
    val postgres = EmbeddedPostgres.start()
    postgres.run("CREATE TABLE JsonbExample(id SERIAL PRIMARY KEY, jsonbValue JSONB)")
    val ctx = TerpalDriver.Postgres(postgres.postgresDatabase)
    val je = JsonbExample(1, JsonValue(MyPerson("Alice", 30)))
    Sql("INSERT INTO JsonbExample (id, jsonbValue) VALUES (1, ${Param.withSer(je.jsonbValue)})").action().runOn(ctx)
    //Sql("""INSERT INTO JsonbExample (id, jsonbValue) VALUES (1, '{"name":"Joe", "age":123}')""").action().runOn(ctx)
    val customers = Sql("SELECT id, jsonbValue FROM JsonbExample").queryOf<JsonbExample>().runOn(ctx)
    println(customers)
  }
}

// Like JsonColumnExample4 but just get the the json column as a single value
object JsonColumnExample5 {

  @Serializable
  data class MyPerson(val name: String, val age: Int)

  @Serializable
  data class JsonbExample(val id: Int, val jsonbValue: JsonValue<MyPerson>)

  suspend fun main() {
    val postgres = EmbeddedPostgres.start()
    postgres.run("CREATE TABLE JsonbExample(id SERIAL PRIMARY KEY, jsonbValue JSONB)")
    val ctx = TerpalDriver.Postgres(postgres.postgresDatabase)
    val je = JsonbExample(1, JsonValue(MyPerson("Alice", 30)))
    //Sql("INSERT INTO JsonbExample (id, jsonbValue) VALUES (1, ${Param.withSer(je.jsonbValue, MyPerson.serializer())})").action().runOn(ctx)
    Sql("""INSERT INTO JsonbExample (id, jsonbValue) VALUES (1, '{"name":"Joe", "age":123}')""").action().runOn(ctx)

    val jsonValues = Sql("SELECT jsonbValue FROM JsonbExample").queryOf<JsonValue<MyPerson>>().runOn(ctx)
    println(jsonValues)

    val parentValues = Sql("SELECT id, jsonbValue FROM JsonbExample").queryOf<JsonbExample>().runOn(ctx)
    println(parentValues)
  }
}

// Using JsonValue to read/write a datatype already supported by kotlinx-serialization
object JsonColumnExample6 {
  @Serializable
  data class JsonbExample(val id: Int, val jsonbValue: JsonValue<List<String>>)

  suspend fun main() {
    val postgres = EmbeddedPostgres.start()
    postgres.run("CREATE TABLE JsonbExample(id SERIAL PRIMARY KEY, jsonbValue JSONB)")
    val ctx = TerpalDriver.Postgres(postgres.postgresDatabase)
    val jsonValue = JsonValue(listOf("Joe", "Jack"))
    val je = JsonbExample(1, jsonValue)
    Sql("INSERT INTO JsonbExample (id, jsonbValue) VALUES (1, ${Param.withSer(jsonValue)})").action().runOn(ctx)

    val jsonValues = Sql("SELECT jsonbValue FROM JsonbExample").queryOf<JsonValue<List<String>>>().runOn(ctx)
    println(jsonValues)

    val parentValues = Sql("SELECT id, jsonbValue FROM JsonbExample").queryOf<JsonbExample>().runOn(ctx)
    println(parentValues)
  }
}

// Mix and match usage of JsonValue and @SqlJsonValue
object JsonColumnExample7 {
  @Serializable
  data class MyPerson(val name: String, val age: Int)

  @Serializable
  data class JsonbExample(val id: Int, @SqlJsonValue val jsonbValue: MyPerson)

  suspend fun main() {
    val postgres = EmbeddedPostgres.start()
    postgres.run("CREATE TABLE JsonbExample(id SERIAL PRIMARY KEY, jsonbValue JSONB)")
    val ctx = TerpalDriver.Postgres(postgres.postgresDatabase)
    val joe = MyPerson("Joe", 123)
    val jack = MyPerson("Jack", 456)

    Sql("INSERT INTO JsonbExample (id, jsonbValue) VALUES (1, ${Param.withSer(JsonValue(joe))})").action().runOn(ctx)

    Sql("INSERT INTO JsonbExample (id, jsonbValue) VALUES (2, ${Param.json(jack)})").action().runOn(ctx)

    val parentValue = Sql("SELECT id, jsonbValue FROM JsonbExample").queryOf<JsonbExample>().runOn(ctx)
    println(parentValue)

    val jsonValue = Sql("SELECT jsonbValue FROM JsonbExample").queryOf<JsonValue<MyPerson>>().runOn(ctx)
    println(jsonValue)
  }
}

// Does not work:
//typealias MyPersonJson = @Serializable @SqlJsonValue JsonColumnExample3.MyPerson
//object JsonColumnExample3 {
//
//  @Serializable
//  data class MyPerson(val name: String, val age: Int)
//
//  @Serializable
//  data class JsonbExample(val id: Int, val jsonbValue: MyPersonJson)
//
//  suspend fun main() {
//    val postgres = EmbeddedPostgres.start()
//    postgres.run("CREATE TABLE JsonbExample(id SERIAL PRIMARY KEY, jsonbValue JSONB)")
//    val ctx = TerpalContext.Postgres(postgres.postgresDatabase)
//    val je = JsonbExample(1, MyPerson("Alice", 30))
//    //Sql("INSERT INTO JsonbExample (id, jsonbValue) VALUES (1, ${Param.withSer(je.jsonbValue, MyPerson.serializer())})").action().runOn(ctx)
//    Sql("""INSERT INTO JsonbExample (id, jsonbValue) VALUES (1, '{"name":"Joe", "age":123}')""").action().runOn(ctx)
//    val customers = Sql("SELECT id, jsonbValue FROM JsonbExample").queryOf<JsonbExample>().runOn(ctx)
//    println(customers)
//  }
//}

// Does not work:
//object JsonColumnExample31 {
//
//  @Serializable
//  data class MyPerson(val name: String, val age: Int)
//
//  @Serializable
//  data class JsonbExample(val id: Int, val jsonbValue: JsonValue<MyPerson>)
//
//  suspend fun main() {
//    val postgres = EmbeddedPostgres.start()
//    postgres.run("CREATE TABLE JsonbExample(id SERIAL PRIMARY KEY, jsonbValue JSONB)")
//    val ctx = TerpalContext.Postgres(postgres.postgresDatabase)
//    val je = JsonbExample(1, JsonValue(MyPerson("Alice", 30)))
//    //Sql("INSERT INTO JsonbExample (id, jsonbValue) VALUES (1, ${Param.withSer(je.jsonbValue, MyPerson.serializer())})").action().runOn(ctx)
//    Sql("""INSERT INTO JsonbExample (id, jsonbValue) VALUES (1, '{"name":"Joe", "age":123}')""").action().runOn(ctx)
//
//    val customers = Sql("SELECT jsonbValue FROM JsonbExample").queryOf<@SqlJsonValue MyPerson>().runOn(ctx)
//    println(customers)
//  }
//}

suspend fun main() {
  JsonColumnExample7.main()
}