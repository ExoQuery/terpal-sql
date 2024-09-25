package io.exoquery.sql

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import io.exoquery.sql.sqlite.CallAfterVersion
import io.exoquery.sql.sqlite.TerpalSchema

object EmptySchema: SqlSchema<QueryResult.Value<Unit>> {
  override val version: Long = 1
  override fun create(driver: SqlDriver): QueryResult.Value<Unit> = QueryResult.Unit
  override fun migrate(
    driver: SqlDriver,
    oldVersion: Long,
    newVersion: Long,
    vararg callbacks: AfterVersion,
  ) = QueryResult.Unit
}

object BasicSchema: SqlSchema<QueryResult.Value<Unit>> {
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
          ownerId INT,
          street VARCHAR,
          zip INT
      );
      """.trimIndent(),
    )
    driver.executeSimple(
      """
      CREATE TABLE IF NOT EXISTS Product(
        id INTEGER PRIMARY KEY,
          description VARCHAR(255),
          sku BIGINT
      );
      """.trimIndent()
    )
    driver.executeSimple(
      """
    CREATE TABLE KmpTestEntity(
        timeLocalDate      INTEGER,                     -- java.time.LocalDate
        timeLocalTime      INTEGER,                     -- java.time.LocalTime
        timeLocalDateTime  INTEGER,                -- java.time.LocalDateTime
        timeInstant        INTEGER,  -- java.time.Instant
        timeLocalDateOpt      INTEGER,
        timeLocalTimeOpt      INTEGER,                     -- java.time.LocalTime
        timeLocalDateTimeOpt  INTEGER,                -- java.time.LocalDateTime
        timeInstantOpt        INTEGER  -- java.time.Instant
    );
      """.trimIndent()
    )
    driver.executeSimple(
      """
      CREATE TABLE TimeEntity(
          sqlDate        INTEGER,                     -- java.sql.Date
          sqlTime        INTEGER,                     -- java.sql.Time
          sqlTimestamp   INTEGER,                -- java.sql.Timestamp
          timeLocalDate      INTEGER,                     -- java.time.LocalDate
          timeLocalTime      INTEGER,                     -- java.time.LocalTime
          timeLocalDateTime  INTEGER,                -- java.time.LocalDateTime
          timeZonedDateTime  INTEGER, -- java.time.ZonedDateTime
          timeInstant        INTEGER, -- java.time.Instant
          timeOffsetTime     INTEGER,      -- java.time.OffsetTime
          timeOffsetDateTime INTEGER  -- java.time.OffsetDateTime
      );
      """.trimIndent()
    )
    driver.executeSimple(
      """
      CREATE TABLE IF NOT EXISTS EncodingTestEntity(
          stringMan VARCHAR(255),
          booleanMan BOOLEAN,
          byteMan SMALLINT,
          shortMan SMALLINT,
          intMan INTEGER,
          longMan BIGINT,
          floatMan FLOAT,
          doubleMan DOUBLE PRECISION,
          byteArrayMan BLOB,
          customMan VARCHAR(255),
          stringOpt VARCHAR(255),
          booleanOpt BOOLEAN,
          byteOpt SMALLINT,
          shortOpt SMALLINT,
          intOpt INTEGER,
          longOpt BIGINT,
          floatOpt FLOAT,
          doubleOpt DOUBLE PRECISION,
          byteArrayOpt BLOB,
          customOpt VARCHAR(255)
      );
      """.trimIndent()
    )
    driver.executeSimple(
      """
      CREATE TABLE IF NOT EXISTS JavaTestEntity(
          bigDecimalMan DECIMAL(5,2),
          javaUtilDateMan BIGINT,
          uuidMan VARCHAR(36),
          bigDecimalOpt DECIMAL(5,2),
          javaUtilDateOpt BIGINT,
          uuidOpt VARCHAR(36)
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

object BasicSchemaTerpal: TerpalSchema<Unit> {
  override val version: Long = 1
  override suspend fun create(driver: Context): Unit {
    Sql(
      """
      CREATE TABLE Person (
          id INTEGER PRIMARY KEY,
          firstName VARCHAR(255),
          lastName VARCHAR(255),
          age INT
      )
      """
    ).action().runOn(driver)
    Sql(
      """
      CREATE TABLE Address (
          ownerId INT,
          street VARCHAR,
          zip INT
      );
      """
    ).action().runOn(driver)
    Sql(
      """
      CREATE TABLE IF NOT EXISTS Product(
        id INTEGER PRIMARY KEY,
          description VARCHAR(255),
          sku BIGINT
      );
      """
    ).action().runOn(driver)
    Sql(
      """
    CREATE TABLE KmpTestEntity(
        timeLocalDate      INTEGER,                     -- java.time.LocalDate
        timeLocalTime      INTEGER,                     -- java.time.LocalTime
        timeLocalDateTime  INTEGER,                -- java.time.LocalDateTime
        timeInstant        INTEGER,  -- java.time.Instant
        timeLocalDateOpt      INTEGER,
        timeLocalTimeOpt      INTEGER,                     -- java.time.LocalTime
        timeLocalDateTimeOpt  INTEGER,                -- java.time.LocalDateTime
        timeInstantOpt        INTEGER  -- java.time.Instant
    );
    """
    ).action().runOn(driver)
    Sql(
      """
      CREATE TABLE TimeEntity(
          sqlDate        INTEGER,                     -- java.sql.Date
          sqlTime        INTEGER,                     -- java.sql.Time
          sqlTimestamp   INTEGER,                -- java.sql.Timestamp
          timeLocalDate      INTEGER,                     -- java.time.LocalDate
          timeLocalTime      INTEGER,                     -- java.time.LocalTime
          timeLocalDateTime  INTEGER,                -- java.time.LocalDateTime
          timeZonedDateTime  INTEGER, -- java.time.ZonedDateTime
          timeInstant        INTEGER, -- java.time.Instant
          timeOffsetTime     INTEGER,      -- java.time.OffsetTime
          timeOffsetDateTime INTEGER  -- java.time.OffsetDateTime
      );
      """
    ).action().runOn(driver)
    Sql(
      """
      CREATE TABLE IF NOT EXISTS EncodingTestEntity(
          stringMan VARCHAR(255),
          booleanMan BOOLEAN,
          byteMan SMALLINT,
          shortMan SMALLINT,
          intMan INTEGER,
          longMan BIGINT,
          floatMan FLOAT,
          doubleMan DOUBLE PRECISION,
          byteArrayMan BLOB,
          customMan VARCHAR(255),
          stringOpt VARCHAR(255),
          booleanOpt BOOLEAN,
          byteOpt SMALLINT,
          shortOpt SMALLINT,
          intOpt INTEGER,
          longOpt BIGINT,
          floatOpt FLOAT,
          doubleOpt DOUBLE PRECISION,
          byteArrayOpt BLOB,
          customOpt VARCHAR(255)
      );
      """
    ).action().runOn(driver)
    Sql(
      """
      CREATE TABLE IF NOT EXISTS JavaTestEntity(
          bigDecimalMan DECIMAL(5,2),
          javaUtilDateMan BIGINT,
          uuidMan VARCHAR(36),
          bigDecimalOpt DECIMAL(5,2),
          javaUtilDateOpt BIGINT,
          uuidOpt VARCHAR(36)
      )
      """
    ).action().runOn(driver)
  }

  override suspend fun migrate(
    driver: Context,
    oldVersion: Long,
    newVersion: Long,
    vararg callbacks: io.exoquery.sql.sqlite.CallAfterVersion) {
  }
}

object WalTestSchema: SqlSchema<QueryResult.Value<Unit>> {
  override val version: Long = 1
  override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
    driver.executeSimple(
      """
      CREATE TABLE MiscTest (
          id INTEGER NOT NULL PRIMARY KEY,
          value TEXT NOT NULL
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

object WalSchemaTerpal: TerpalSchema<Unit> {
  override val version: Long = 1
  override suspend fun create(driver: Context): Unit {
    Sql(
      """
      CREATE TABLE MiscTest (
          id INTEGER NOT NULL PRIMARY KEY,
          value TEXT NOT NULL
      )
      """
    ).action().runOn(driver)
  }

  override suspend fun migrate(driver: Context, oldVersion: Long, newVersion: Long, vararg callbacks: CallAfterVersion) = Unit
}