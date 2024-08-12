package io.exoquery.sql.examples

import io.exoquery.sql.Param
import io.exoquery.sql.jdbc.*
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

    val ctx = object: TerpalContext.Postgres(postgres.postgresDatabase) {
      override val additionalDecoders =
        super.additionalDecoders + JdbcDecoderAny(ByteContent::class) { ctx, i -> ByteContent(ctx.row.getBinaryStream(i)) }
      override val additionalEncoders =
        super.additionalEncoders + JdbcEncoderAny(Types.BLOB, ByteContent::class) { ctx, v: ByteContent, i -> ctx.stmt.setBinaryStream(i, v.bytes) }
    }

    val (RED, BLUE) = "\u001B[31m" to "\u001B[34m"
    val image = ByteContent.bytesFrom(ByteArrayInputStream("${RED}Hello, ${BLUE}World!".toByteArray()))

    ctx.run(Sql("INSERT INTO images (content) VALUES (${Param.contextual(image)})").action())
    val customers = ctx.run(Sql("SELECT * FROM images").queryOf<Image>())
    customers.map { println("${it.id} - ${it.content.bytes.readAllBytes().toString(Charsets.UTF_8)}") }

    //val module = SerializersModule { contextual(ImageFileContent::class, JdbcAnySerializer) }

  }
}

suspend fun main() {
  ContextualColumnClob.main()
}