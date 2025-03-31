package io.exoquery.sql.examples

import io.exoquery.sql.Sql
import io.exoquery.controller.jdbc.TerpalDriver
import io.exoquery.controller.runOn
import io.exoquery.sql.Param
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object NewtypeColumn {

  @JvmInline
  value class Email(val value: String)

  object EmailSerialzier: KSerializer<Email> {
    override val descriptor = PrimitiveSerialDescriptor("Email", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, email: Email) = encoder.encodeString(email.value)
    override fun deserialize(decoder: Decoder): Email = Email(decoder.decodeString())
  }

  @Serializable
  data class Customer(val id: Int, val firstName: String, val lastName: String, @Serializable(with = EmailSerialzier::class) val email: Email)

  suspend fun main() {
    val postgres = EmbeddedPostgres.start()
    postgres.run("CREATE TABLE customers (id SERIAL PRIMARY KEY, firstName TEXT, lastName TEXT, email TEXT)")
    val ctx = TerpalDriver.Postgres(postgres.postgresDatabase)

    val firstName = "Alice"
    val lastName = "Smith"
    val email = Email("alice.smith@someplace.com")
    Sql("INSERT INTO customers (firstName, lastName, email) VALUES ($firstName, $lastName, ${Param.withSer(email, EmailSerialzier)})").action().runOn(ctx)
    val customers = ctx.run(Sql("SELECT * FROM customers").queryOf<Customer>())
    println(customers)

    //val module = SerializersModule { contextual(Email::class, EmailSerialzier) }
    //val json = Json { serializersModule = module }
    //println(json.encodeToString(ListSerializer(Customer.serializer()), customers))
  }
}

suspend fun main() {
  NewtypeColumn.main()
}