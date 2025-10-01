

<p align="center">
<img src="https://github.com/user-attachments/assets/f9a9ac27-47e2-429e-997a-beb72a1bb81e" width=70% height=70% >
</p>

# terpal-sql

Terpal is a Kotlin Multiplatform library that allows you to write SQL queries using interpolated strings
in an SQL-injection-safe way. Inspired by Scala libraries such as Doobie and Zio-Jdbc, Terpal
suspends the splicing of "$dollar $sign $variables" in `SQL("...")` strings and preserves them in a separate data structure until they can be
safely injected into an SQL statement.

```kotlin
val ds: DataSource = PGSimpleDataSource(...)
val ctx = TerpalDriver.Postgres(ds)

// Let's try a pesky SQL injection attack:
val name = "'Joe'; DROP TABLE Person"

// Boom! The `Person` table will be dropped:
ds.connection.use { conn ->
  conn.createStatement().execute("SELECT * FROM Person WHERE name = $name")
}

// No problem! The `Person` table will be safe:
Sql("SELECT * FROM Person WHERE name = $name").queryOf<Person>().runOn(ctx)
// Behind the scenes:
// val query = "SELECT * FROM Person WHERE name = ?", params = listOf(Param(name))
// conn.prepareStatement(query).use { stmt -> stmt.setString(1, name); stmt.executeQuery() }
```
For a deep dive on how this works, have Have a look at the [Terpal Compiler Plugin](https://github.com/ExoQuery/Terpal).

In addition Terpal allows you to decode the results of SQL queries into Kotlin data classes using
the kotlinx-serialization library.

```kotlin
// Annotate a class using the kotlinx-serialization library
@Serializable
data class Person(val id: Int, val firstName: String, val lastName: String)

// Declare a Terpal context
val ctx = TerpalDriver.Postgres.fromConfig("mydb")

// Run a Query
val person: List<Person> = Sql("SELECT id, firstName, lastName FROM person WHERE id = $id").queryOf<Person>().runOn(ctx)
```

Terpal SQL:
* Uses no reflection!
* Uses no code-generation!
* Allows $dollar_sign_variables to be used safely in SQL queries!
* Does not require queries to be written in a separate file.
* Is built on top of kotlinx-serialization.
* Works idiomatically with Kotlin coroutines suspended functions.

# Getting Started

Currently Terpal is supported 
* **On the JVM** using JDBC with: PostgreSQL, MySQL, SQL Server, Oracle, SQLite, and H2. 
* **On Android, iOS, OSX, Linux and Windows** with SQLite

Fistly, be sure that you have the following repositories defined:
```kotlin
repositories {
  gradlePluginPortal()
  mavenCentral()
  mavenLocal()
}
```


## Using JDBC
When using JDBC, add the following to your `build.gradle.kts` file:

```kotlin
plugins {
    kotlin("jvm") version "2.1.0" // Currently the plugin is only available for Kotlin-JVM
    id("io.exoquery.terpal-plugin") version "2.1.0-2.0.0.PL"
    kotlin("plugin.serialization") version "2.1.0"
}

dependencies {
    api("io.exoquery:terpal-sql-jdbc:2.0.0.PL-1.2.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    // Your database driver for example postgres:
    implementation("org.postgresql:postgresql:42.7.0")
}
```

A Terpal context is equivalent to a SQLite driver. It is the object that manages the connection to the database.

#### When using JDBC:
```kotlin
// You either construct a context from a JDBC DataSource:
val ctx = TerpalDriver.Postgres(dataSource)

// Or you can use the fromConfig helper to read from a configuration file
val ctx = TerpalDriver.Postgres.fromConfig("myPostgresDB")

// ====== application.conf ======
// myPostgresDB {
//   dataSourceClassName=org.postgresql.ds.PGSimpleDataSource
//   dataSource.user=postgres
//   dataSource.password=mysecretpassword
//   dataSource.portNumber=35433
//   dataSource.serverName=localhost 
// }
```
Have a look at the Terpal-SQL [Sample Project](https://github.com/ExoQuery/terpal-sql-sample) for more details.

## Using Android

For Android development, add the following to your `build.gradle.kts` file:

```kotlin
plugins {
    kotlin("android") version "2.1.0"
    id("io.exoquery.terpal-plugin") version "2.1.0-2.0.0.PL"
    kotlin("plugin.serialization") version "2.1.0"
}

dependencies {
    api("io.exoquery:terpal-sql-android:2.0.0.PL-1.2.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("androidx.sqlite:sqlite-framework:2.4.0")
}
```

### Construct from ApplicationContext
Use the `TerpalAndroidDriver.fromApplicationDriver` method to create a terpal context from an Android Application Driver
```kotlin
val ctx = 
  TerpalAndroidDriver.fromApplicationContext(
    databaseName = "mydb",
    applicationContext = ApplicationProvider.getApplicationContext(),
    // Optional: A TerpalSchema object defining the database schema and migrations. Similar to an SqlDelight SqlSchema object.
    //           Alternatively, a SqlDelight SqlSchema object or any SupportSQLiteOpenHelper.Callback can be used.
    //           See the section below on how to define a schema.
    schema = MyTerpalSchema,
    // Optional: A setting describing how to pool connections. The default is a single-threaded pool.
    poolingModel =
      // The default mode that uses Android WAL compatibility mode
      PoolingMode.SingleSessionWal
      // Use this to get MVCC-like transaction isolation and real multi-reader concurrency. 
      // Mutiple instances of SupportSQLiteOpenHelper are used so be careful with memory consumption.
      PoolingMode.MultipleReaderWal(2)
      // Use Pre-WAL mode for compatibility with older Android versions
      PoolingMode.SingleSessionLegacy
  )
// Run a query:
val person: List<Person> = Sql("SELECT * FROM Person").queryOf<Person>().runOn(ctx)
```

### Constuct from SupportSQLiteOpenHelper
Use the `TerpalAndroidDriver.fromSingleOpenHelper` method to create a terpal context from a single instance of `SupportSQLiteOpenHelper`
```kotlin
val myOpenHelperInstance = FrameworkSQLiteOpenHelperFactory().create(
  SupportSQLiteOpenHelper.Configuration.builder(androidApplicationContext)
    .name(databaseName)
    // Other options e.g. callback, factory, etc.
    .build(),
)
val ctx =
  TerpalAndroidDriver.fromSingleOpenHelper(
    openHelper = myOpenHelperInstance
  )
// Run a query:
val person: List<Person> = Sql("SELECT * FROM Person").queryOf<Person>().runOn(ctx)
```

Use the `TerpalAndroidDriver.fromSingleSession` method to create a terpal context from a single instance of `SupportSQLiteDatabase`
```kotlin
val myDatabaseInstance = myOpenHelperInstance.writableDatabase
val ctx =
  TerpalAndroidDriver.fromSingleSession(
    database = myDatabaseInstance
  )
// Run a query:
val person: List<Person> = Sql("SELECT * FROM Person").queryOf<Person>().runOn(ctx)
```

> Note that most of the constructors on the `TerpalAndroidDriver` object are suspended functions. This is because
> creating a `SupportSQLiteDatabase` frequently involves database schema creation and migration that is done
> as part of the SupportSQLiteOpenHelper.Callback. These contexts are required to be create from a coroutine
> to avoid blocking the main thread.

## Using iOS, OSX, Linux and Windows

For iOS, OSX, Linux and Windows development, with Kotlin Multiplatform, add the following to your `build.gradle.kts` file:
```kotlin
plugins {
    kotlin("multiplatform") version "2.1.0"
    id("io.exoquery.terpal-plugin") version "2.1.0-2.0.0.PL"
    kotlin("plugin.serialization") version "2.1.0"
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
                // Note that terpal-sql-native supports iOS, OSX, Linux and Windows
                api("io.exoquery:terpal-runtime:2.1.0-2.0.0.PL")
                implementation("io.exoquery:terpal-sql-native:2.0.0.PL-1.2.0")
            }
        }
    }
}
```

The create the TerpalNativeDriver using one of the following constructors.

Use the `TerpalNativeDriver.fromSchema` method to create a native Terpal context from a `TerpalSchema` object.

```kotlin
val ctx = 
  TerpalNativeDriver.fromSchema(
    schema = MyTerpalSchema
  )
```

The `TerpalNativeDriver` uses SQLighter as the underlying database driver.
```kotlin
val ctx =
  TerpalNativeDriver.fromSchema(
    // Optional: A TerpalSchema object defining the database schema and migrations. Similar to an SqlDelight SqlSchema object.
    //           Alternatively, a SqlDelight SqlSchema object can be used.
    //           See the section below on how to define a schema.
    schema = MyTerpalSchema,
    // Name of the database file to use
    databaseName = "mydb",
    // Base-Path fo the database file to use (this will be the Kotlin working directory by default)
    basePath = "/my/custom/path"
  )
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
Use the `actionReturning<Datatype>()` method to run an action that returns a value.

```kotlin
val firstName = "Joe"
val lastName = "Bloggs"
val id = 
  Sql("INSERT INTO Person (firstName, lastName) VALUES ($firstName, $lastName) RETURNING id")
    .actionReturning<Int>().runOn(ctx)

// You can also decode the results into a data class
val person: Person = 
  Sql("INSERT INTO Person (firstName, lastName) VALUES ($firstName, $lastName) RETURNING id, firstName, lastName")
    .queryOf<Person>().runOn(ctx)

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
```kotlin
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
val ctx = TerpalDriver.Postgres.fromConfig("mydb")
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
When a variable used in a Sql clause e.g. `Sql("... $dollar_sign_variable ...")` it needs
to be wrapped in a `Param` object.

### Transient Values

Frequently, serializeable classes will have additional values that are not stored in the database
but rather can be computed by default. For example:
```kotlin
@Serializable
data class Person(val name: String, val age: Int) {
  val title = "Mr " + name
}

// Given the database schema:
// CREATE TABLE person (name TEXT, age INT)
```
Make sure to add the annotation `@Transient` to the field that is not stored in the database!
```kotlin
@Serializable
data class Person(val name: String, val age: Int) {
  @Transient
  val title = "Mr " + name
}
```
If you forget to add this annotation, the Kotlin Serialization library will attempt to read the field
from the database row i.e. as though the database schema had a third `title` column 
(and then write it into the `val title` field... which should technically be impossible, see the note).

> NOTE: As of version 1.6.2 the behavior of kotlinx-serialization regarding `val` fields in data classes is highly
> unintuitive. The Kotlin serialization library will create a hidden constructor that takes the `val` fields marked
> inside the class body that technically are not accessible at all. It is as though the `Person` class actually 
> looks like this:
> ```kotlin
> // This primary constructor is hidden:
> data class Person(val name: String, val age: Int, val title: String, val title: String) {
>  constructor(name: String, age: Int, title: Int = "Mr " + name) : this(name, age, name)
> }
> ```
> This is why the `@Transient` annotation is required to prevent the Kotlin Serialization library from trying to
> alter the class structure in this bizarre way.



### Automatic Wrapping

You can use the `io.exoquery.sql.Param` object to splice a variable in a `Sql(...)` clause.
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
 - Kotlin Multiplatform Time Types: `kotlinx.datetime.LocalDate`, `kotlinx.datetime.LocalTime`, `kotlinx.datetime.LocalDateTime`, `kotlinx.datetime.Instant`
 - SQL Time Types: `java.sql.Date`, `java.sql.Timestamp`, `java.sql.Time`
 - Other: BigDecimal, ByteArray
 - Note that in all the time-types Nano-second granularity is not supported. It will be rounded to the nearest millisecond.

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
Sql("INSERT INTO customers (firstName, lastName, email) VALUES ($firstName, $lastName, ${Param.withSer(email, EmailSerialzier)})")
  .action().runOn(ctx)
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
val ctx = object: TerpalDriver.Postgres(postgres.postgresDatabase) {
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

Terpal supports nested data classes and will "flatten" the data classes when decoding the results.
Note that both the outer class and inner class must be annotated with `@Serializable` in most cases.
```kotlin
@Serializable
data class Person(val id: Int, val name: Name, val age: Int)
@Serializable
data class Name(val firstName: String, val lastName: String)

// Use a regular table schema. Terpal knows how to flatten the data classes.
// CREATE TABLE person (id INT, firstName TEXT, lastName TEXT, age INT)

val people: List<Person> = Sql("SELECT * FROM people").queryOf<Person>().runOn(ctx)
println(people)
//> Person(id=1, name=Name(firstName=Joe, lastName=Bloggs), age=30)
```

### IN (...) Clauses

Terpal supports lifting lists of arbitrary objects for `IN (...)` clauses. The
same encoding mechanisms that are used for regular parameters are used for lists.

Use the `io.exoquery.sql.Params.invoke(...)` family of functions to lift
lists of elements (similar to the way `Param.invoke(...)` is used for single elements).
```kotlin
val peopleQuery = Sql("SELECT * FROM people WHERE firstName IN ${Params("Joe", "Jim")}").queryOf<Person>()
// peopleQuery.sql = "SELECT * FROM people WHERE firstName IN (?, ?)"

println(peopleQuery.runOn(ctx))
//> List(Person(id=1, firstName=Joe, lastName=Bloggs), Person(id=2, firstName=Jim, lastName=Roogs))
```
Make sure to *not* add parentheses around the `Params(...)` clause. This is done automatically.

If you want to use an instance of `List<T>` in an `IN (...)` clause you can use the `Params.list`
convenience function.
```kotlin
val ids = listOf(1, 2, 3)
val people = Sql("SELECT * FROM people WHERE id IN ${Params.list(ids)}").queryOf<Person>().runOn(ctx)
```

> Note that since `column IN ()` is invalid SQL syntax, therefore invoking `Params.list(emptyList)`
> will result in `column IN (null)` being synthesized instead.
> ```kotlin
> val emptyList = listOf<Int>()
> val peopleQuery = Sql("SELECT * FROM people WHERE id IN ${Params.list(emptyList)}").queryOf<Person>()
> // peopleQuery.sql = "SELECT * FROM people WHERE id IN (null)"
> ```

### JSON Valued Columns
> NOTE: This is currently only supported in Postgres

#### Using the SqlJsonValue Annotation

In Postgres you can store JSON data in a column. Terpal can automatically decode the JSON data into a Kotlin data class in two ways.
The first way is to add a `@SqlJsonValue` on the data class field. This will tell Terpal to decode that particular column
as JSON when the parent-object is queried.

```kotlin
@Serializeable
data class Person(val name: String, val age: Int)
@Serializeable
data class JsonExample(val id: Int, @SqlJsonValue val person: Person)

// Inserting an example value directly:
Sql("""INSERT INTO JsonExample (id, person) VALUES (1, '{"name": "Joe", "value": 30}')""").action().runOn(ctx)
// Retrieve it like this:
val values: List<JsonExample> = Sql("SELECT id, person FROM JsonExample").queryOf<JsonExample>().runOn(ctx)
//> List(JsonExample(1, Person(name=Joe, value=30)))
```
This list of `JsonExample` parent-objects is returned.

> Note how you cannot query for the `Person` class directly in the above example because it is not annotated with `@SqlJsonValue`.
> Querying for it will make Terpal try to fetch the Person.name and Person.age columns because it will treat
> the Person class as a regular data class.
> ```
> Sql("SELECT person FROM JsonExample").queryOf<Person>().runOn(ctx)
> //> Column mismatch. The columns from the SQL ResultSet metadata did not match the expected columns from the deserialized type
> //> SQL Columns (1): [(0)value:jsonb], Class Columns (2): [(0)name:kotlin.String, (1)age:kotlin.Int]
> ```
> The next example will show how to get around this.

You can also place the `@SqlJsonValue` annotation on the actual child data-class. The advantage of this is that Terpal
will know to decode the JSON data into the child data-class directly (not only when it is queried as part of the parent).

```kotlin
@SqlJsonValue
@Serializeable
data class Person(val name: String, val age: Int)
@Serializeable
data class JsonExample(val id: Int, val person: Person)

val person = Person("Joe", 30)
Sql("""INSERT INTO JsonExample (id, person) VALUES (1, '{"name": "Joe", "value": 30}')""").action().runOn(ctx)

//> List(JsonExample(1, Person(name=Joe, value=30)))

// Can insert the Person class directly:
Sql("INSERT INTO JsonExample (id, person) VALUES (1, ${Param.withSer(person)})").action().runOn(ctx)

// Can select the Person class directly:
val values: List<Person> = Sql("SELECT person FROM JsonExample").queryOf<Person>().runOn(ctx)
//> List(Person(name=Joe, value=30))

// Can select the the parent entity:
val values: List<JsonExample> = Sql("SELECT id, person FROM JsonExample").queryOf<JsonExample>().runOn(ctx)
//> List(JsonExample(1, Person(name=Joe, value=30)))
```

You can find more examples using JSON columns in the [Json Column Examples](terpal-sql-jdbc/src/test/kotlin/io/exoquery/sql/examples/JsonColumnExample.kt)
documentation.

#### Using the JsonValue Wrapper

If you want more explicit control over how JSON data is encoded/decoded you can use the `JsonValue` wrapper.
The advantage of this approach is that you can store arbitrary JSON data in a column and decode it
into a datatype without a wrapper-class (i.e. what the `Person` class was doing above).
```kotlin
@Serializeable
data class JsonExample(val id: Int, val names: JsonValue<List<String>>)

// This:
Sql("""INSERT INTO JsonExample (id, names) VALUES (1, '["Joe", "Jack"]')""").action().runOn(ctx)
// Is equivalent to:
Sql("INSERT INTO JsonExample (id, names) VALUES (1, ${JsonValue(listOf("Joe", "Jack"))})").action().runOn(ctx)

// Can select the JSON data directly:
val values: List<JsonValue<List<String>>> = Sql("SELECT names FROM JsonExample").queryOf<JsonValue<List<String>>>().runOn(ctx)
//> List(JsonValue(List("Joe", "Jack")))

// Can select the parent entity:
val values: List<JsonExample> = Sql("SELECT id, names FROM JsonExample").queryOf<JsonExample>().runOn(ctx)
//> List(JsonExample(1, JsonValue(List("Joe", "Jack"))))
```

#### Mix and Match

You can mix and match the `@SqlJsonValue` annotation and the `JsonValue` wrapper. This is useful when you want to
define a field using `@SqlJsonValue` but then need to insert and/or query that JSON-column by itself.
```kotlin
@Serializeable
data class Person(val name: String, val age: Int)
@Serializeable
data class JsonExample(val id: Int, @SqlJsonValue val person: Person)

// Insert the JSON data directly
Sql("INSERT INTO JsonExample (id, person) VALUES (1, ${Param(JsonValue(Person("Joe", 30)))})").action().runOn(ctx)
// A helper function Param.json has been introduced to make this easier
Sql("INSERT INTO JsonExample (id, person) VALUES (1, ${Param.json(Person("Joe", 30))})").action().runOn(ctx)

// Select the JSON data directly
val values: List<JsonValue<Person>> = Sql("SELECT person FROM JsonExample").queryOf<JsonValue<Person>>().runOn(ctx)
//> List(JsonValue(Person(name=Joe, value=30)))

// Select the parent entity
val values: List<JsonExample> = Sql("SELECT id, person FROM JsonExample").queryOf<JsonExample>().runOn(ctx)
//> List(JsonExample(1, Person(name=Joe, value=30)))
```

You can find more examples using JSON columns in the [Json Column Examples](terpal-sql-jdbc/src/test/kotlin/io/exoquery/sql/examples/JsonColumnExample.kt) 
documentation.

### Playing well with other Kotlinx Formats

When using Terpal-SQL with kotlinx-serialization with other formats such as JSON in real-world situations, you may
frequently need either different encodings or even entirely different schemas for the same data. For example, you may
want to encode a `LocalDate` using the SQL `DATE` type, but when sending the data to a REST API you may want to encode 
the same `LocalDate` as a `String` in ISO-8601 format (i.e. using DateTimeFormatter.ISO_LOCAL_DATE).

There are several ways to do this in Kotlinx-serialization, I will discuss two of them.

#### Using a Contextual Serializer
The simplest way to have a different encoding for the same data in different contexts is to use a contextual serializer.
```kotlin
@Serializable
data class Customer(val id: Int, val firstName: String, val lastName: String, @Contextual val createdAt: LocalDate)

// Database Schema:
// CREATE TABLE customers (id INT, first_name TEXT, last_name TEXT, created_at DATE)

// This Serializer encodes the LocalDate as a String in ISO-8601 format and it will only be used for JSON encoding.
object DateAsIsoSerializer: KSerializer<LocalDate> {
  override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
  override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.format(DateTimeFormatter.ISO_LOCAL_DATE))
  override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString(), DateTimeFormatter.ISO_LOCAL_DATE)
}

// When working with the database, the LocalDate will be encoded as a SQL DATE type. The Terpal Driver knows
// will behave this way by default when a field is marked as @Contextual.
val ctx = TerpalDriver.Postgres.fromConfig("mydb")
val customer = Customer(1, "Alice", "Smith", LocalDate.of(2021, 1, 1))
Sql("INSERT INTO customers (first_name, last_name, created_at) VALUES (${customer.firstName}, ${customer.lastName}, ${customer.createdAt})").action().runOn(ctx)

// When encoding the data as JSON, the make sure to specify the DateAsIsoSerializer in the serializers-module.
val json = Json {
  serializersModule = SerializersModule {
    contextual(LocalDate::class, DateAsIsoSerializer)
  }
}
val jsonCustomer = json.encodeToString(Customer.serializer(), customer)
println(jsonCustomer)
//> {"id":1,"firstName":"Alice","lastName":"Smith","createdAt":"2021-01-01"}
```
See the [Playing Well using Different Encoders](terpal-sql-jdbc/src/test/kotlin/io/exoquery/sql/examples/PlayingWell_DifferentEncoders.kt)
example for more details.

#### Using Row-Surrogate Encoder
When the changes in encoding between the Database and JSON are more complex, you may want to use a row-surrogate encoder.

A row-surrogate encoder will take a data-class and copy it into another data-class (i.e. the surrogate data-class) whose schema is appropriate
for the target format. The surrogate data-class needs to also be serializable and know how to create itself from the original data-class.

```kotlin
// Create the "original" data class
@Serializable
data class Customer(
  val id: Int, 
  val firstName: String, 
  val lastName: String, 
  @Serializable(with = DateAsIsoSerializer::class) val createdAt: LocalDate
)

// Create the "surrogate" data class
@Serializable
data class CustomerSurrogate(val id: Int, val firstName: String, val lastName: String, @Contextual val createdAt: LocalDate) {
  fun toCustomer() = Customer(id, firstName, lastName, createdAt)
  companion object {
    fun fromCustomer(customer: Customer): CustomerSurrogate {
      return CustomerSurrogate(customer.id, customer.firstName, customer.lastName, customer.createdAt)
    }
  }
}
```

Then create a surrogate serializer which uses the surrogate data-class to encode the original data-class.
```kotlin
object CustomerSurrogateSerializer: KSerializer<Customer> {
  override val descriptor = CustomerSurrogate.serializer().descriptor
  override fun serialize(encoder: Encoder, value: Customer) = 
    encoder.encodeSerializableValue(CustomerSurrogate.serializer(), CustomerSurrogate.fromCustomer(value))
  override fun deserialize(decoder: Decoder): Customer = 
    decoder.decodeSerializableValue(CustomerSurrogate.serializer()).toCustomer()
}
```

Then use the surrogate serializer when reading data from the database.
```kotlin
// You can then use the surrogate class when reading/writing information from the database:
val customers = ctx.run(Sql("SELECT * FROM customers").queryOf<Customer>(CustomerSurrogateSerializer))

// ...and use the regular data-class/serializer when encoding/decoding to JSON
println(Json.encodeToString(ListSerializer(Customer.serializer()), customers))
//> [{"id":1,"firstName":"Alice","lastName":"Smith","createdAt":"2021-01-01"}]
```

See the [Playing Well using Row-Surrogate Encoder](terpal-sql-jdbc/src/test/kotlin/io/exoquery/sql/examples/PlayingWell_RowSurrogate.kt)
section for more details.

### Using Array Columns

Currently Terpal-SQL does not support direct encoding/decoding of generic
collections. In order to use array columns create a value-class with a specific
collection type and use a combination of `@Contextual`, custom encoders, and
`Param.contextual` in order to achieve the desired behavior.

The following example illustrates how to do this for Postgres array columns.
```kotlin
// Given the table schema looks like this:
// CREATE TABLE friend (firstName TEXT, lastName TEXT, nickNames TEXT[])

// First create custom datatypes:
@JvmInline
value class MyStringList(val value: List<String>)
@Serializable
data class Friend(val id: Int, val firstName: String, val lastName: String, @Contextual val nickNames: MyStringList)

// Then create the encoder/decoder
val MyStringListEncoder: JdbcEncoderAny<MyStringList> =
  JdbcEncoderAny(Types.VARCHAR, MyStringList::class) { ctx, v, i ->
    val arr = ctx.session.createArrayOf(JDBCType.VARCHAR.toString(), v.value.toTypedArray())
    ctx.stmt.setArray(i, arr)
  }
val MyStringListDecoder: JdbcDecoderAny<MyStringList> =
  JdbcDecoderAny(MyStringList::class) { ctx, i ->
    MyStringList((ctx.row.getArray(i).array as Array<String>).toList())
  }

// Pass them into the context
val ctx = TerpalDriver.Postgres(
  postgres.postgresDatabase,
  JdbcEncodingConfig.Default(setOf(MyStringListEncoder), setOf(MyStringListDecoder))
)

// Then configuration is complete!
// You can now use the custom list datatype MyStringList during insertion (this uses the encoders)
val joeNicknames = MyStringList(listOf("Joey", "Jay"))
Sql("INSERT INTO person (firstName, lastName, age) VALUES ('Joe', 'Bloggs', ${Param.contextual(joeNicknames)})").action().runOn(ctx)

// As well as during selection (this uses the decoders)
val friends = Sql("SELECT * FROM friend WHERE").queryOf<Friend>()

// ...or both at the same time:
val joeOrJill = MyStringList(listOf("Joe", "Jill"))
val friends = Sql("SELECT * FROM friend WHERE firstName = ANY(${Param.contextual(joeOrJill)})").queryOf<Friend>()

println(friends)
//> [Friend(firstName=Joe, lastName=Bloggs, nickNames=MyStringList(value=[Joey, Jay])), Friend(firstName=Jill, lastName=Doggs, nickNames=MyStringList(value=[Jilly, Jillaroo]))]
```
For a working example of the above instructions see [UsingPostgresArray](terpal-sql-jdbc/src/test/kotlin/io/exoquery/sql/examples/UsingPostgresArray.kt).

## IntelliJ Language Injection Support

Terpal-SQL is optimized for IntelliJ [Language Injection](https://www.jetbrains.com/help/idea/using-language-injections.html) support. As such, IntelliJ will understand that
strings inside of the `Sql(...)` clause should be treated as SQL constructs and provide custom auto-completion as such:

<p align="center">
<img src="https://github.com/user-attachments/assets/2d211073-bacf-49ae-9f2f-545c9558a544" width=70% height=70% >
</p>

It is important to note that this auto-complete is based on what database IntelliJ believes the current SQL code snippet is actually related to, and conveying this information to IntelliJ may require some manual steps.
Typically you need to click on the [Intention-actions](https://www.jetbrains.com/help/idea/intention-actions.html) i.e. small yellow lightbulb menu (Alt+Enter) and then on Run query in console. Based on the database that you select, IntelliJ will deduce appropriate hints and code completion.

<p align="center">
<img src="https://github.com/user-attachments/assets/f0a95a6d-2cf7-479f-872e-428f1040307e" width=70% height=70% >
</p>

As in all things related to auto-complete and IDEs, your personal mileage may vary. Particularly when it comes to other integrated tools such as Copilot or Jetbrains AI.
One additional note, when looking for correct Intention-action, make sure that your cursor is in the SQL string itself not in the surrounding `Sql(...)` function.
The following two screenshots demonstrate this.



<p align="center">
<img src="https://github.com/user-attachments/assets/03fe9db2-19da-452b-9b92-acacff1ed48e" width=40% height=40% >
<img src="https://github.com/user-attachments/assets/f09a3f97-d70a-4a8b-b7e6-afb6aa790113" width=40% height=40% >
</p>
