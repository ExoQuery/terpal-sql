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
val person: List<Person> = Sql("SELECT * FROM person WHERE id = $id").queryOf<Person>().runOn(ctx)
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

## Batch Actions

## Transactions

## Custom Parameters
(list currently supported types)

## Advanced Decoding

### Nested Data Classes

### Wrapped Datatypes

### Custom/Contextual Datatypes

### Sharing with Kotlinx JSON Serialization
(i.e. Surrogate Encoders)




