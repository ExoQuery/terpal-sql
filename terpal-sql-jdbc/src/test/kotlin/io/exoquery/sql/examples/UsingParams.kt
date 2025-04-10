package io.exoquery.sql.examples

import io.exoquery.sql.Sql
import io.exoquery.controller.jdbc.JdbcControllers
import io.exoquery.controller.runOn
import io.exoquery.sql.Params
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.serialization.Serializable

object UsingParams {

  @Serializable
  data class Customer(val id: Int, val firstName: String, val lastName: String, val age: Int)

  suspend fun main() {
    val postgres = EmbeddedPostgres.start()
    postgres.run("CREATE TABLE person (id SERIAL PRIMARY KEY, firstName TEXT, lastName TEXT, age INT)")
    val ctx = JdbcControllers.Postgres(postgres.postgresDatabase)

    Sql("INSERT INTO person (firstName, lastName, age) VALUES ('Joe', 'Bloggs', 123)").action().runOn(ctx)
    Sql("INSERT INTO person (firstName, lastName, age) VALUES ('Jim', 'Roggs', 456)").action().runOn(ctx)
    Sql("INSERT INTO person (firstName, lastName, age) VALUES ('Jill', 'Doggs', 789)").action().runOn(ctx)

    val sql = Sql("SELECT * FROM person WHERE firstName IN ${Params("Joe", "Jill")}").queryOf<Customer>()
    println(sql.sql)
    println(sql.params)

    val customers = sql.runOn(ctx)
    println(customers)

  }
}

suspend fun main() {
  UsingParams.main()
}
