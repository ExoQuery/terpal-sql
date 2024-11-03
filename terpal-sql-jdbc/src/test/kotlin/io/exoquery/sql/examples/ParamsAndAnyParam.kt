package io.exoquery.sql.examples

import io.exoquery.sql.Sql
import io.exoquery.sql.jdbc.TerpalDriver
import io.exoquery.sql.runOn
import io.exoquery.sql.Param
import io.exoquery.sql.Params
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ParamsAndAnyParam {

  @Serializable
  data class Customer(val id: Int, val firstName: String, val lastName: String, val age: Int)

  suspend fun main() {
    val postgres = EmbeddedPostgres.start()
    postgres.run("CREATE TABLE person (id SERIAL PRIMARY KEY, firstName TEXT, lastName TEXT, age INT)")
    val ctx = TerpalDriver.Postgres(postgres.postgresDatabase)

    Sql("INSERT INTO person (firstName, lastName, age) VALUES ('Joe', 'Bloggs', 123)").action().runOn(ctx)
    Sql("INSERT INTO person (firstName, lastName, age) VALUES ('Jim', 'Roggs', 456)").action().runOn(ctx)
    Sql("INSERT INTO person (firstName, lastName, age) VALUES ('Jill', 'Doggs', 789)").action().runOn(ctx)

    val sql = Sql("SELECT * FROM person WHERE firstName IN ${Params("Joe", "Jill")}").queryOf<Customer>()
    println(sql.sql)
    println(sql.params)

    // TODO test combos of 1,2 and 3 names


    val customers = ctx.run(sql)
    println(customers)

  }
}

suspend fun main() {
  ParamsAndAnyParam.main()
}