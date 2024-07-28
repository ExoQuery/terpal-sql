package io.exoquery.sql

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

class PerfSchema(val maxId: Int): SqlSchema<QueryResult.Value<Unit>> {
  override val version: Long = 1

  val clearQuery = "DELETE FROM perf"

  val loadQuery by lazy {
    val concattedValues =
      (1..maxId).map { id ->
        // for some reason physically concating them does not work and only excutes the first statement
        "($id, 'name$id', 0)"
      }.joinToString(",")

    "INSERT INTO perf (id, name, age) VALUES ${concattedValues};"
  }

  override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
    println("------------------------ Table Create ------------------------\n")
    driver.executeSimple(
      """
      CREATE TABLE perf (
          id INTEGER PRIMARY KEY,
          name VARCHAR(255),
          age INT
      );
      """.trimIndent()
    )

    // (1 until maxId).forEach { id ->
    //   // for some reason physically concating them does not work and only excutes the first statement
    //   driver.executeSimple("INSERT INTO perf (id, name, age) VALUES ($id, 'name$id', 0)")
    //}

    // For some reason when you do it like this it only inserts the first statement. Maybe because sqliter only excutes up to the 1st newline? Need to look into it.
    //val concattedQuery = (1 until maxId).map { id ->
    //   // for some reason physically concating them does not work and only excutes the first statement
    //   "INSERT INTO perf (id, name, age) VALUES ($id, 'name$id', 0);"
    //}.joinToString("\n")
    //driver.executeSimple(concattedQuery)

    // Don't rely on this to load the query, should do it as part of the test
    //driver.executeSimple(loadQuery)

    return QueryResult.Unit
  }

  override fun migrate(
    driver: SqlDriver,
    oldVersion: Long,
    newVersion: Long,
    vararg callbacks: AfterVersion,
  ) = QueryResult.Unit
}

object PersonSchema: SqlSchema<QueryResult.Value<Unit>> {
  override val version: Long = 1
  override fun create(driver: SqlDriver): QueryResult.Value<Unit> {

    driver.executeSimple(
      """
      CREATE TABLE Person (
          id INTEGER PRIMARY KEY,
          firstName VARCHAR(255),
          lastName VARCHAR(255),
          age INT
      );
      """.trimIndent()
    )
    driver.executeSimple(
      """
      CREATE TABLE Address (
          ownerId INTEGER,
          street VARCHAR(255),
          zip INTEGER
      );
      """.trimIndent()
    )

    return QueryResult.Unit
  }

  override fun migrate(
    driver: SqlDriver,
    oldVersion: Long,
    newVersion: Long,
    vararg callbacks: AfterVersion,
  ) = QueryResult.Unit
}
