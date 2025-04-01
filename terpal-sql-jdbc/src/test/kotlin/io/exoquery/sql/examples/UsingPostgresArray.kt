package io.exoquery.sql.examples

import io.exoquery.sql.Param
import io.exoquery.sql.Sql
import io.exoquery.controller.jdbc.DatabaseController
import io.exoquery.controller.runOn
import io.exoquery.controller.jdbc.JdbcDecoderAny
import io.exoquery.controller.jdbc.JdbcEncoderAny
import io.exoquery.controller.jdbc.JdbcEncodingConfig
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.sql.JDBCType
import java.sql.Types

object UsingPostgresArray {

  @JvmInline
  value class MyStringList(val value: List<String>)

  val MyStringListEncoder: JdbcEncoderAny<MyStringList> =
    JdbcEncoderAny(Types.VARCHAR, MyStringList::class) { ctx, v, i ->
      val arr = ctx.session.createArrayOf(JDBCType.VARCHAR.toString(), v.value.toTypedArray())
      ctx.stmt.setArray(i, arr)
    }
  val MyStringListDecoder: JdbcDecoderAny<MyStringList> =
    JdbcDecoderAny(MyStringList::class) { ctx, i ->
      MyStringList((ctx.row.getArray(i).array as Array<String>).toList())
    }

  @Serializable
  data class Friend(val firstName: String, val lastName: String, @Contextual val nickNames: MyStringList)

  suspend fun main() {
    val postgres = EmbeddedPostgres.start()
    postgres.run("CREATE TABLE friend (firstName TEXT, lastName TEXT, nickNames TEXT[])")
    val ctx = DatabaseController.Postgres(
      postgres.postgresDatabase,
      JdbcEncodingConfig.Default(setOf(MyStringListEncoder), setOf(MyStringListDecoder))
    )

    val joeNicknames = MyStringList(listOf("Joey", "Jay"))
    val jimNicknames = MyStringList(listOf("Jimmy", "Jimbo"))
    val jillNicknames = MyStringList(listOf("Jilly", "Jillaroo"))

    Sql("INSERT INTO friend (firstName, lastName, nickNames) VALUES ('Joe', 'Bloggs', ${Param.contextual(joeNicknames)})").action().runOn(ctx)
    Sql("INSERT INTO friend (firstName, lastName, nickNames) VALUES ('Jim', 'Roggs', ${Param.contextual(jimNicknames)})").action().runOn(ctx)
    Sql("INSERT INTO friend (firstName, lastName, nickNames) VALUES ('Jill', 'Doggs', ${Param.contextual(jillNicknames)})").action().runOn(ctx)

    val joeOrJill = MyStringList(listOf("Joe", "Jill"))
    val sql = Sql("SELECT * FROM friend WHERE firstName = ANY(${Param.contextual(joeOrJill)})").queryOf<Friend>()
    println(sql.sql)
    println(sql.params)

    val customers = sql.runOn(ctx)
    println(customers)

  }
}

suspend fun main() {
  UsingPostgresArray.main()
}