package io.exoquery.sql.postgres

import io.exoquery.sql.Param
import io.exoquery.sql.examples.run
import io.exoquery.sql.jdbc.Sql
import io.exoquery.sql.jdbc.TerpalContext
import io.exoquery.sql.jdbc.runOn
import io.kotest.core.spec.style.FreeSpec
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
import io.exoquery.sql.postgres.TypeModuleSpecData.Customer
import io.exoquery.sql.postgres.TypeModuleSpecData.Email
import io.exoquery.sql.postgres.TypeModuleSpecData.EmailSerialzier
import io.exoquery.sql.postgres.TypeModuleSpecData.JsonEmailSerialzier
import io.kotest.matchers.shouldBe

object TypeModuleSpecData {
  data class Email(val value: String)

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
}

class TypeModuleSpec: FreeSpec ({

  "should be able to use different serializers for the same type in different contexts" {
    val postgres = EmbeddedPostgres.start()
    postgres.run("CREATE TABLE customers (id SERIAL PRIMARY KEY, firstName TEXT, lastName TEXT, email TEXT)")
    val ctx =
      object: TerpalContext.Postgres(postgres.postgresDatabase) {
        override val module = SerializersModule { contextual(Email::class, EmailSerialzier) }
      }

    val firstName = "Alice"
    val lastName = "Smith"
    val email = Email("alice.smith@someplace.com")
    Sql("INSERT INTO customers (firstName, lastName, email) VALUES ($firstName, $lastName, ${Param.withSer(email, EmailSerialzier)})").action().runOn(ctx)
    val customers = ctx.run(Sql("SELECT * FROM customers").queryOf<Customer>())
    customers.first() shouldBe Customer(1, "Alice", "Smith", Email("alice.smith@someplace.com"))

    val jsonModule = SerializersModule { contextual(Email::class, JsonEmailSerialzier) }
    val json = Json { serializersModule = jsonModule }
    val encoded = json.encodeToString(ListSerializer(Customer.serializer()), customers)
    encoded shouldBe """[{"id":1,"firstName":"Alice","lastName":"Smith","email":"alice.smith at someplace.com"}]"""
  }
})