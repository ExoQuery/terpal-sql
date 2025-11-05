package io.exoquery.sql.postgres

import io.exoquery.sql.*
import io.exoquery.sql.encodingdata.*
import io.exoquery.sql.Sql
import io.exoquery.controller.runOn
import io.exoquery.controller.runActions
import io.kotest.core.spec.style.FreeSpec
import java.time.ZoneId
import io.exoquery.controller.r2dbc.R2dbcController
import io.exoquery.sql.TestDatabasesR2dbc

class EncodingSpec: FreeSpec({

  val cf = TestDatabasesR2dbc.postgres
  val ctx: R2dbcController by lazy { R2dbcController(encodingConfig = encodingConfig, connectionFactory = cf) }

  suspend fun runActions(actions: String) = ctx.runActions(actions)

  beforeSpec { SchemaInitR2dbc.ensureApplied(ctx) }

  beforeEach {
    // The main table used across many tests
    runActions("DELETE FROM EncodingTestEntity")
  }

  "encodes and decodes nullables - not nulls" {
    insert(EncodingTestEntity.regular).runOn(ctx)
    val res = Sql("SELECT * FROM EncodingTestEntity").queryOf<EncodingTestEntity>().runOn(ctx)
    verify(res.first(), EncodingTestEntity.regular)
  }

  "encodes and decodes custom impls nullables - not nulls" {
    insert(EncodingTestEntityImp.regular).runOn(ctx)
    val res = Sql("SELECT * FROM EncodingTestEntity").queryOf<EncodingTestEntityImp>().runOn(ctx)
    verify(res.first(), EncodingTestEntityImp.regular)
  }

  "encodes and decodes custom impls nullables - nulls" {
    insert(EncodingTestEntityImp.empty).runOn(ctx)
    val res = Sql("SELECT * FROM EncodingTestEntity").queryOf<EncodingTestEntityImp>().runOn(ctx)
    verify(res.first(), EncodingTestEntityImp.empty)
  }

  "encodes and decodes custom value-classes nullables - not nulls" {
    insert(EncodingTestEntityVal.regular).runOn(ctx)
    val res = Sql("SELECT * FROM EncodingTestEntity").queryOf<EncodingTestEntityVal>().runOn(ctx)
    verify(res.first(), EncodingTestEntityVal.regular)
  }

  "encodes and decodes custom value-classes nullables - nulls" {
    insert(EncodingTestEntityVal.empty).runOn(ctx)
    val res = Sql("SELECT * FROM EncodingTestEntity").queryOf<EncodingTestEntityVal>().runOn(ctx)
    verify(res.first(), EncodingTestEntityVal.empty)
  }

  "encodes and decodes batch" {
    insertBatch(listOf(EncodingTestEntity.regular, EncodingTestEntity.regular)).runOn(ctx)
    val res = Sql("SELECT * FROM EncodingTestEntity").queryOf<EncodingTestEntity>().runOn(ctx)
    verify(res[0], EncodingTestEntity.regular)
    verify(res[1], EncodingTestEntity.regular)
  }

  "encodes and decodes nullables - nulls" {
    insert(EncodingTestEntity.empty).runOn(ctx)
    val res = Sql("SELECT * FROM EncodingTestEntity").queryOf<EncodingTestEntity>().runOn(ctx)
    verify(res.first(), EncodingTestEntity.empty)
  }

  "Encode/Decode Additional Java Types - regular" {
    runActions("DELETE FROM JavaTestEntity")
    insert(JavaTestEntity.regular).runOn(ctx)
    val actual = Sql("SELECT * FROM JavaTestEntity").queryOf<JavaTestEntity>().runOn(ctx).first()
    verify(actual, JavaTestEntity.regular)
  }

  "Encode/Decode Additional Java Types - empty" {
    runActions("DELETE FROM JavaTestEntity")
    insert(JavaTestEntity.empty).runOn(ctx)
    val actual = Sql("SELECT * FROM JavaTestEntity").queryOf<JavaTestEntity>().runOn(ctx).first()
    verify(actual, JavaTestEntity.empty)
  }

  "Encode/Decode KMP Types" {
    runActions("DELETE FROM KmpTestEntity")
    insert(KmpTestEntity.regular).runOn(ctx)
    val actual = Sql("SELECT * FROM KmpTestEntity").queryOf<KmpTestEntity>().runOn(ctx).first()
    verify(actual, KmpTestEntity.regular)
  }

  "Encode/Decode Other Time Types" {
    runActions("DELETE FROM TimeEntity")
    val zid = ZoneId.systemDefault()
    val timeEntity = TimeEntity.make(zid)
    insert(timeEntity).runOn(ctx)
    val actual = Sql("SELECT * FROM TimeEntity").queryOf<TimeEntity>().runOn(ctx).first()
    assert(timeEntity == actual)
  }

  "Encode/Decode Other Time Types ordering" {
    runActions("DELETE FROM TimeEntity")

    val zid = ZoneId.systemDefault()
    val timeEntityA = TimeEntity.make(zid, TimeEntity.TimeEntityInput(2022, 1, 1, 1, 1, 1, 0))
    val timeEntityB = TimeEntity.make(zid, TimeEntity.TimeEntityInput(2022, 2, 2, 2, 2, 2, 0))

    insert(timeEntityA).runOn(ctx)
    insert(timeEntityB).runOn(ctx)

    assert(timeEntityB.sqlDate > timeEntityA.sqlDate)
    assert(timeEntityB.sqlTime > timeEntityA.sqlTime)
    assert(timeEntityB.sqlTimestamp > timeEntityA.sqlTimestamp)
    assert(timeEntityB.timeLocalDate > timeEntityA.timeLocalDate)
    assert(timeEntityB.timeLocalTime > timeEntityA.timeLocalTime)
    assert(timeEntityB.timeLocalDateTime > timeEntityA.timeLocalDateTime)
    assert(timeEntityB.timeZonedDateTime > timeEntityA.timeZonedDateTime)
    assert(timeEntityB.timeInstant > timeEntityA.timeInstant)
    assert(timeEntityB.timeOffsetTime > timeEntityA.timeOffsetTime)
    assert(timeEntityB.timeOffsetDateTime > timeEntityA.timeOffsetDateTime)

    val actual =
      Sql("""
          SELECT * FROM TimeEntity 
          WHERE 
            sqlDate > ${timeEntityA.sqlDate} 
            AND sqlTime > ${timeEntityA.sqlTime}
            AND sqlTimestamp > ${timeEntityA.sqlTimestamp}
            AND timeLocalDate > ${timeEntityA.timeLocalDate}
            AND timeLocalTime > ${timeEntityA.timeLocalTime}
            AND timeLocalDateTime > ${timeEntityA.timeLocalDateTime}
            AND timeZonedDateTime > ${timeEntityA.timeZonedDateTime}
            AND timeInstant > ${timeEntityA.timeInstant}
            AND timeOffsetTime > ${timeEntityA.timeOffsetTime}
            AND timeOffsetDateTime > ${timeEntityA.timeOffsetDateTime}
          """
      ).queryOf<TimeEntity>().runOn(ctx).first()

    assert(actual == timeEntityB)
  }
})
