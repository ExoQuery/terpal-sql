package io.exoquery.sql.examples

import io.exoquery.sql.JsonValue
import io.exoquery.sql.Param
import io.exoquery.sql.SqlJsonValue
import io.exoquery.sql.examples.JsonColumnExample3.MyPerson
import io.exoquery.sql.jdbc.Sql
import io.exoquery.sql.jdbc.TerpalContext
import io.exoquery.sql.jdbc.runOn
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer

//data class JsonValue<T>(val value: T, val serializer: KSerializer<T>) {
//  companion object {
//    inline operator fun <reified T> invoke(value: T): JsonValue<T> = JsonValue(value, serializer<T>())
//  }
//}
//
//object JsonValueSerializer: KSerializer<JsonValue<*>> {
//  override val descriptor = SerialDescriptor("JsonValue", Seria)
//  override fun deserialize(decoder: Decoder): JsonValue<*> {
//    TODO("Not yet implemented")
//  }
//
//  override fun serialize(encoder: Encoder, value: JsonValue<*>) {
//    TODO("Not yet implemented")
//  }
//
//}


object JsonColumnExample1 {

  @SqlJsonValue
  @Serializable
  data class MyPerson(val name: String, val age: Int)

  @Serializable
  data class JsonbExample(val id: Int, val jsonbValue: MyPerson)

  suspend fun main() {
    val postgres = EmbeddedPostgres.start()
    postgres.run("CREATE TABLE JsonbExample(id SERIAL PRIMARY KEY, jsonbValue JSONB)")
    val ctx = TerpalContext.Postgres(postgres.postgresDatabase)
    val je = JsonbExample(1, MyPerson("Alice", 30))
    Sql("INSERT INTO JsonbExample (id, jsonbValue) VALUES (1, ${Param.withSer(je.jsonbValue, MyPerson.serializer())})").action().runOn(ctx)
    val customers = Sql("SELECT id, jsonbValue FROM JsonbExample").queryOf<JsonbExample>().runOn(ctx)
    println(customers)
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
    val ctx = TerpalContext.Postgres(postgres.postgresDatabase)
    val je = JsonbExample(1, MyPerson("Alice", 30))
    //Sql("INSERT INTO JsonbExample (id, jsonbValue) VALUES (1, ${Param.withSer(je.jsonbValue, MyPerson.serializer())})").action().runOn(ctx)
    Sql("""INSERT INTO JsonbExample (id, jsonbValue) VALUES (1, '{"name":"Joe", "age":123}')""").action().runOn(ctx)
    val customers = Sql("SELECT id, jsonbValue FROM JsonbExample").queryOf<JsonbExample>().runOn(ctx)
    println(customers)
  }
}

typealias MyPersonJson = @Serializable @SqlJsonValue JsonColumnExample3.MyPerson

object JsonColumnExample3 {

  @Serializable
  data class MyPerson(val name: String, val age: Int)

  @Serializable
  data class JsonbExample(val id: Int, val jsonbValue: MyPersonJson)

  suspend fun main() {
    val postgres = EmbeddedPostgres.start()
    postgres.run("CREATE TABLE JsonbExample(id SERIAL PRIMARY KEY, jsonbValue JSONB)")
    val ctx = TerpalContext.Postgres(postgres.postgresDatabase)
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
    val ctx = TerpalContext.Postgres(postgres.postgresDatabase)
    val je = JsonbExample(1, JsonValue(MyPerson("Alice", 30)))
    //Sql("INSERT INTO JsonbExample (id, jsonbValue) VALUES (1, ${Param.withSer(je.jsonbValue, MyPerson.serializer())})").action().runOn(ctx)
    Sql("""INSERT INTO JsonbExample (id, jsonbValue) VALUES (1, '{"name":"Joe", "age":123}')""").action().runOn(ctx)
    val customers = Sql("SELECT id, jsonbValue FROM JsonbExample").queryOf<JsonbExample>().runOn(ctx)
    println(customers)
  }
}

suspend fun main() {
  JsonColumnExample4.main()
}