# terpal-sql

Terpal is a Kotlin library that allows you to write SQL queries in Kotlin using interpolated strings
in an SQL-injection-safe way. Inspired by Scala libraries such as Doobie and Zio-Jdbc, Terpal
delays suspends the "$dollar $sign $variables" in strings in a separate data structure until they can be
safely inject into an SQL statement.

TODO large diagram about teral variable suspension

In addition Terpal allows you to decode the results of SQL queries into Kotlin data classes using
the kotlinx-serialization library.

```kotlin
// Annotate a class using the kotlinx-serialization library
@Serializable
data class Person(val id: Int, val firstName: String, val lastName: String)

// Declare a Terpal context
val ctx = TerpalContext.Postgres.fromConfig("mydb")

// Run a Query
val person: List<Person> = Sql("SELECT id, firstName, lastName FROM person WHERE id = $id").queryOf<Person>().runOn(ctx)
```

# Installation

Add the following to your `build.gradle.kts` file:

```kotlin
plugins {
    kotlin("jvm") version "2.0.0" // Currently the plugin is only available for Kotlin-JVM
    id("io.exoquery.terpal-plugin") version "2.0.0-0.2.0"
    kotlin("plugin.serialization") version "2.0.0"
}

dependencies {
    api("io.exoquery:terpal-sql-jdbc:2.0.0-0.2.0")

    api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Your databse driver for example postgres:
    implementation("org.postgresql:postgresql:42.7.0")
}

// Make sure you have these repositories setup
repositories {
  gradlePluginPortal()
  mavenCentral()
  mavenLocal()
}
```

Declaring a context:
```kotlin
// You either construct a context from a JDBC DataSource:
val ctx = TerpalContext.Postgres(dataSource)

// Or you can use the fromConfig helper to read from a configuration file
val ctx = TerpalContext.Postgres.fromConfig("myPostgresDB")

// ====== application.conf ======
myPostgresDB {
  dataSourceClassName=org.postgresql.ds.PGSimpleDataSource
  dataSource.user=postgres
  dataSource.password=mysecretpassword
  dataSource.portNumber=35433
  dataSource.serverName=localhost 
}
```

# Features

## Queries and Actions

Terpal SQL supports queries i.e. SELECT, actions i.e. INSERT, UPDATE, DELETE and actions that return a value i.e. INSERT... RETURNING.
Actions are supported both for single rows and in batch-mode. Terpal uses batch optimization (e.g. JDBC addBatch/excutBatch) for where possible.

### Queries
```kotlin
@Serializable
data class Person(val id: Int, val firstName: String, val lastName: String)
// Run a Query. Note that when doing a `SELECT *` the fields and  field-ordering in the table must match the data class 
val person: List<Person> = Sql("SELECT * FROM Person WHERE id = $id").queryOf<Person>().runOn(ctx)

// You can also decode the results into a sequence
val person: Flow<Person> = Sql("SELECT * FROM Person WHERE id = $id").queryOf<Person>().streamOn(ctx)

// The output can also be a single scalar
val firstName = Sql("SELECT firstName FROM Person WHERE id = $id").queryOf<String>().runOn(ctx)
```

### Actions
```kotlin
// Regular actions:
val firstName = "Joe"
val lastName = "Bloggs"
Sql("INSERT INTO Person (firstName, lastName) VALUES ($firstName, $lastName)").action().runOn(ctx)
```

### Actions that return a value
```kotlin
// Actions that return a value:
val firstName = "Joe"
val lastName = "Bloggs"
val id = Sql("INSERT INTO Person (firstName, lastName) VALUES ($firstName, $lastName) RETURNING id").actionReturning<Int>().runOn(ctx)

// You can also decode the results into a data class
val person: Person = Sql("INSERT INTO Person (firstName, lastName) VALUES ($firstName, $lastName) RETURNING id, firstName, lastName").queryOf<Person>().runOn(ctx)

// Note that the fields and field-ordering in the table must match the data class (we are assuming the id column is auto-generated)
// @Serializable
// data class Person(val id: Int, val firstName: String, val lastName: String)
```

Note that the actual syntax of the actual syntax of the returning query will very based on your database,
for example in SQL Server you would use:
```kotlin
val id = Sql("INSERT INTO Person (firstName, lastName) OUTPUT INSERTED.id VALUES ($firstName, $lastName)").actionReturning<Int>().runOn(ctx)
```

> For other databases e.g. Oracle you may need to specify the columns being returned as a list:
> ```kotlin
> Sql("INSERT INTO Person (firstName, lastName) VALUES ($firstName, $lastName)").actionReturning<Person>("id", "firstName", "lastName").runOn(ctx)
> ```
> When a list of columns is specified in `actionReturning`, the are passed into the statement-preparation of the underlying
> database driver. For example `Connection.prepareStatement(sql, arrayOf("id", "firstName", "lastName"))`.


## Batch Actions
Terpal batch actions are supported for both regular actions and actions that return a value.
```kotlin
import io.exoquery.sql.examples.Simple_SqlServer

val people = listOf(
  Person(1, "Joe", "Bloggs"),
  Person(2, "Jim", "Roogs")
)

// Regular batch action
SqlBatch { p: Person ->
  "INSERT INTO Person (id, firstName, lastName) VALUES (${p.id}, ${p.firstName}, ${p.lastName})")
}.values(people).action().runOn(ctx)

// Batch action that returns a value
val ids = SqlBatch { p: Person ->
  "INSERT INTO Person (id, firstName, lastName) VALUES (${p.id}, ${p.firstName}, ${p.lastName}) RETURNING id")
}.values(people).actionReturning<Int>().runOn(ctx)
```
Batch actions can return records. They can also take streaming inputs as well as outputs.
```
// Batch queries can take a stream of values using the `.values(Flow<T>)` method.
val people: Flow<Person> = flowOf(Person(1, "Joe", "Bloggs"), Person(2, "Jim", "Roogs"), ...)
// Use stream-on to stream the outputs
val outputs: Flow<Person> =
  val ids = SqlBatch { p: Person ->
    "INSERT INTO Person (id, firstName, lastName) VALUES (${p.id}, ${p.firstName}, ${p.lastName}) RETURNING id, firstName, lastName")
  }.values(people).actionReturning<Int>().streamOn(ctx)
```

In each case, the batch action uses the driver-level batch optimization (e.g. JDBC addBatch/executeBatch) wherever possible.
For example, SQLite does not support batch actions returning values.

> Note that similar to regular actions, the actual syntax of the returning query will very based on your database,
> for example for Oracle you would use:
> ```kotlin
> val ids = SqlBatch { p: Person ->
>  "INSERT INTO Person (id, firstName, lastName) VALUES (${p.id}, ${p.firstName}, ${p.lastName})")
> }.values(people).actionReturning<Person>("id", "firstName", "lastName").runOn(ctx)

## Transactions
Terpal supports transactions using the `transaction` method. The transaction method takes a lambda that
contains the actions to be performed in the transaction. If the lambda throws an exception the transaction
is rolled back, otherwise it is committed.
```kotlin
val ctx = TerpalContext.Postgres.fromConfig("mydb")
ctx.transaction {
  Sql("INSERT INTO Person (id, firstName, lastName) VALUES (1, 'Joe', 'Bloggs')").action().run()
  Sql("INSERT INTO Person (id, firstName, lastName) VALUES (2, 'Jim', 'Roogs')").action().run()
}
// Note that in the body of `transaction` you can use the `run` method without passing a context since the context is passed as a reciever.
```

If the transaction is aborted in the middle (e.g. by throwing an exception) the transaction is rolled back.
```kotlin
try {
  ctx.transaction {
    Sql("INSERT INTO Person (id, firstName, lastName) VALUES (1, 'Joe', 'Bloggs')").action().run()
    throw Exception("Abort")
    Sql("INSERT INTO Person (id, firstName, lastName) VALUES (2, 'Jim', 'Roogs')").action().run()
  }
} catch (e: Exception) {
  // The transaction is rolled back
}
```


## Custom Parameters
When a varible used in a Sql clause e.g. `Sql("... $dollar_sign_variable ...")` it needs
to be wrapped in a `Param` object.

### Automatic Wrapping

```kotlin
val id = 123
val manualWrapped = Sql("SELECT * FROM Person WHERE id = ${Param(id)}").queryOf<Person>().runOn(ctx)
```

This will happen automatically when using Kotlin primitives and some date-types.
```kotlin
val id = 123
val automaticWrapped = Sql("SELECT * FROM Person WHERE id = $id").queryOf<Person>().runOn(ctx)
```

The following types are automatically wrapped:
 - Primitives: String, Int, Long, Short, Byte, Float, Double, Boolean
 - Time Types: `java.util.Date`, LocalDate, LocalTime, LocalDateTime, ZonedDateTime, Instant, OffsetTime, OffsetDateTime
 - SQL Time Types: `java.sql.Date`, `java.sql.Timestamp`, `java.sql.Time`
 - Other: BigDecimal, ByteArray

### Custom Wrapped Types

If you want to use use a custom datatype in an `Sql(...)` clause you need to wrap it in a `Param` object.
Typically you need to define a primitive wrapper-serializer in order to do this.
```kotlin
@JvmInline
value class Email(val value: String)

val email: Email = Email("...")
Sql("INSERT INTO customers (firstName, lastName, email) VALUES ($firstName, $lastName, ${email})").action().runOn(ctx)
//> /my/project/path/NewtypeColumn.kt:46:5 The interpolator Sql does not have a wrap function for the type: my.package.Email.

// Define a serializer for the custom type
object EmailSerializer : KSerializer<Email> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Email", PrimitiveKind.STRING)
  override fun serialize(encoder: Encoder, value: Email) = encoder.encodeString(value.value)
  override fun deserialize(decoder: Decoder) = Email(decoder.decodeString())
}

// Then use in the Param wrapper (`withSer` is an alias for `withSerializer`) giving it a serializer. 
Sql("INSERT INTO customers (firstName, lastName, email) VALUES ($firstName, $lastName, ${Param.withSer(email, EmailSerialzier)})").action().runOn(ctx)
```

Keep in mind that if you want to use this custom datatype in a parent case-class you will need to let kotlinx-serialization
know that the EmailSerializer needs to be used. The simplest way to do this is to use the `@Serializable(with = ...)` annotation on the parent class.
```kotlin
data class Customer(
  val id: Int, 
  val firstName: String, val lastName: String, 
  @Serializable(with = EmailSerializer::class) val email: Email
)

// The you can query the data class as normal:
val customers: List<Customer> = Sql("SELECT * FROM customers").queryOf<Customer>().runOn(ctx)
```

There are several other ways to do this, have a look at the [Custom Serializers](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md#custom-serializers) section kotlinx-serialization documentation for more information.

### Custom Primitives

In some situations, with a custom datatype you may need to control how it is encoded in the database driver.
Take for example this highly custom type representing input byptes:
```kotlin
data class ByteContent(val bytes: InputStream) {
  companion object {
    fun bytesFrom(input: InputStream) = ByteContent(ByteArrayInputStream(input.readAllBytes()))
  }
}

// Instead of making the ByteContent a serializable type, we mark it as a contextual type.
@Serializable
data class Image(val id: Int, @Contextual val content: ByteContent)

// Then we provide an encoder and decoder for it on the driver-level (i.e. JDBC) when creating the context:
val ctx = object: TerpalContext.Postgres(postgres.postgresDatabase) {
  override val additionalDecoders =
    super.additionalDecoders + JdbcDecoderAny.fromFunction { ctx, i -> ByteContent(ctx.row.getBinaryStream(i)) }
  override val additionalEncoders =
    super.additionalEncoders + JdbcEncoderAny.fromFunction(Types.BLOB) { ctx, v: ByteContent, i -> ctx.stmt.setBinaryStream(i, v.bytes) }
}

// We can then read the content:
val customers = ctx.run(Sql("SELECT * FROM images").queryOf<Image>())
```
Note that in order to splice a contextual datatype into a `Sql(...)` clause you will need to use `Param.contextual`.
```kotlin
val data: ByteContent = ...
Sql("INSERT INTO images (id, content) VALUES ($id, ${Param.contextual(data)})").action().runOn(ctx)
```

Have a look at the [Contextual Column Clob](terpal-sql-jdbc/src/test/kotlin/io/exoquery/sql/examples/ContextualColumnCustom.kt) example for more details.

### Nested Data Classes



### Playing well with other Kotlinx Formats

