package io.exoquery.sql.examples

import io.exoquery.sql.jdbc.Sql
import io.exoquery.sql.jdbc.TerpalContext
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

object PlayingWell_DifferentEncoders {
  object DateAsLongSerializer: KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeLong(value.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    override fun deserialize(decoder: Decoder): LocalDate = Instant.ofEpochMilli(decoder.decodeLong()).atZone(ZoneOffset.UTC).toLocalDate()
  }

  @Serializable
  data class Customer(val id: Int, val firstName: String, val lastName: String, @Contextual val createdAt: LocalDate)

  suspend fun main() {
    val postgres = EmbeddedPostgres.start()
    postgres.run("CREATE TABLE customers (id SERIAL PRIMARY KEY, first_name TEXT, last_name TEXT, created_at DATE)")
    val ctx = TerpalContext.Postgres(postgres.postgresDatabase)
    ctx.run(Sql("INSERT INTO customers (first_name, last_name, created_at) VALUES (${id("Alice")}, ${id("Smith")}, ${id(LocalDate.of(2021, 1, 1))})").action())
    val customers = ctx.run(Sql("SELECT * FROM customers").queryOf<Customer>())

    val json = Json { serializersModule = SerializersModule { contextual(LocalDate::class, DateAsLongSerializer) } }
    println(json.encodeToString(ListSerializer(Customer.serializer()), customers))
  }
}

suspend fun main() {
  PlayingWell_DifferentEncoders.main()
}