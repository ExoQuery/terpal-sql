package io.exoquery.sql.examples

import io.exoquery.controller.jdbc.JdbcDecoderAny
import io.exoquery.controller.jdbc.JdbcEncoderAny
import io.exoquery.controller.jdbc.JdbcEncodingConfig
import io.exoquery.controller.jdbc.JdbcControllers
import io.exoquery.controller.runOn
import io.exoquery.sql.Param
import io.exoquery.sql.Sql
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.sql.Types

object ContextualColumnClob {

  data class ByteContent(val bytes: InputStream) {
    companion object {
      fun bytesFrom(input: InputStream) = ByteContent(ByteArrayInputStream(input.readAllBytes()))
    }
  }

  @Serializable
  data class Image(val id: Int, @Contextual val content: ByteContent)


  suspend fun main() {
    val postgres = EmbeddedPostgres.start()
    postgres.run("CREATE TABLE images (id SERIAL PRIMARY KEY, content BYTEA)")

    val ctx = JdbcControllers.Postgres(
      postgres.postgresDatabase,
      JdbcEncodingConfig(
        setOf(JdbcEncoderAny(Types.BLOB, ByteContent::class) { ctx, v: ByteContent, i ->
          ctx.stmt.setBinaryStream(
            i,
            v.bytes
          )
        }),
        setOf(JdbcDecoderAny(ByteContent::class) { ctx, i -> ByteContent(ctx.row.getBinaryStream(i)) })
      )
    )

    val (RED, BLUE) = "\u001B[31m" to "\u001B[34m"
    val image = ByteContent.bytesFrom(ByteArrayInputStream("${RED}Hello, ${BLUE}World!".toByteArray()))

    Sql("INSERT INTO images (content) VALUES (${Param.contextual(image)})").action().runOn(ctx)
    val customers = Sql("SELECT * FROM images").queryOf<Image>().runOn(ctx)
    customers.map { println("${it.id} - ${it.content.bytes.readAllBytes().toString(Charsets.UTF_8)}") }

    //val module = SerializersModule { contextual(ImageFileContent::class, JdbcAnySerializer) }

  }
}

suspend fun main() {
  ContextualColumnClob.main()
}
