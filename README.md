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
(list currently supported types)

## Advanced Decoding

### Nested Data Classes

### Wrapped Datatypes

### Custom/Contextual Datatypes

### Sharing with Kotlinx JSON Serialization
(i.e. Surrogate Encoders)




