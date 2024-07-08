package io.exoquery.sql.examples

import io.exoquery.sql.jdbc.Sql
import io.exoquery.sql.jdbc.TerpalContext
import io.exoquery.sql.jdbc.runOn
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object PlayingWell_DifferentEncoders {
  object DateAsIsoSerializer: KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.format(DateTimeFormatter.ISO_LOCAL_DATE))
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString(), DateTimeFormatter.ISO_LOCAL_DATE)
  }

  @Serializable
  data class Customer(val id: Int, val firstName: String, val lastName: String, @Contextual val createdAt: LocalDate)

  suspend fun main() {
    val postgres = EmbeddedPostgres.start()
    postgres.run("CREATE TABLE customers (id SERIAL PRIMARY KEY, first_name TEXT, last_name TEXT, created_at DATE)")
    val ctx = TerpalContext.Postgres(postgres.postgresDatabase)
    val customer = Customer(1, "Alice", "Smith", LocalDate.of(2021, 1, 1))
    //ctx.run(Sql("INSERT INTO customers (first_name, last_name, created_at) VALUES (${id("Alice")}, ${id("Smith")}, ${id(LocalDate.of(2021, 1, 1))})").action())
    Sql("INSERT INTO customers (first_name, last_name, created_at) VALUES (${customer.firstName}, ${customer.lastName}, ${customer.createdAt})").action().runOn(ctx)
    val customers = Sql("SELECT * FROM customers").queryOf<Customer>().runOn(ctx)

    val json = Json { serializersModule = SerializersModule { contextual(LocalDate::class, DateAsIsoSerializer) } }
    println(json.encodeToString(ListSerializer(Customer.serializer()), customers))
  }
}

suspend fun main() {
  PlayingWell_DifferentEncoders.main()
}