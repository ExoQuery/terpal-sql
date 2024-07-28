package io.exoquery.sql.examples

import io.exoquery.sql.run
import io.exoquery.sql.Param
import io.exoquery.sql.jdbc.*
import io.exoquery.sql.runOn
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object NewtypeColumnContextual_DifferentEncoders {

  @JvmInline
  value class Email(val value: String)

  object EmailSerialzier: KSerializer<Email> {
    override val descriptor = PrimitiveSerialDescriptor("Email", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, email: Email) = encoder.encodeString(email.value)
    override fun deserialize(decoder: Decoder): Email = Email(decoder.decodeString())
  }

  object JsonEmailSerialzier: KSerializer<Email> {
    override val descriptor = PrimitiveSerialDescriptor("Email", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, email: Email) = encoder.encodeString(email.value.replace("@", " at "))
    override fun deserialize(decoder: Decoder): Email = Email(decoder.decodeString().replace(" at ", "@"))
  }

  @Serializable
  data class Customer(val id: Int, val firstName: String, val lastName: String, @Contextual val email: Email)

  suspend fun main() {
    val postgres = EmbeddedPostgres.start()
    postgres.run("CREATE TABLE customers (id SERIAL PRIMARY KEY, firstName TEXT, lastName TEXT, email TEXT)")
    val ctx =
      TerpalContext.Postgres(
        postgres.postgresDatabase,
        JdbcEncodingConfig(
          module = SerializersModule { contextual(Email::class, EmailSerialzier) }
        )
      )

    val firstName = "Alice"
    val lastName = "Smith"
    val email = Email("alice.smith@someplace.com")
    Sql("INSERT INTO customers (firstName, lastName, email) VALUES ($firstName, $lastName, ${Param.withSer(email, EmailSerialzier)})").action().runOn(ctx)
    val customers = ctx.run(Sql("SELECT * FROM customers").queryOf<Customer>())
    println(customers)

    val jsonModule = SerializersModule { contextual(Email::class, JsonEmailSerialzier) }
    val json = Json { serializersModule = jsonModule }
    println(json.encodeToString(ListSerializer(Customer.serializer()), customers))
  }
}

suspend fun main() {
  NewtypeColumnContextual_DifferentEncoders.main()
}