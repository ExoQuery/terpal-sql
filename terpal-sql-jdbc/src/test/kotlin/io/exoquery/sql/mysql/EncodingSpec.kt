package io.exoquery.sql.mysql

import io.exoquery.sql.*
import io.exoquery.sql.encodingdata.*
import io.exoquery.sql.Sql
import io.exoquery.controller.jdbc.JdbcControllers
import io.exoquery.controller.runOn
import io.kotest.core.spec.style.FreeSpec
import java.time.ZoneId

class EncodingSpec: FreeSpec({
  val ds = TestDatabases.mysql
  val ctx by lazy { JdbcControllers.Mysql(ds, encodingConfig) }

  beforeEach {
    ds.run("DELETE FROM EncodingTestEntity")
  }

  "encodes and decodes nullables - not nulls" {
    insert(EncodingTestEntity.regular).runOn(ctx)
    val res = Sql("SELECT * FROM EncodingTestEntity").queryOf<EncodingTestEntity>().runOn(ctx)
    verify(res.first(), EncodingTestEntity.regular)
  }

  "encodes and decodes batch" {
    insertBatch(listOf(EncodingTestEntity.regular, EncodingTestEntity.regular)).runOn(ctx)
    val res = Sql("SELECT * FROM EncodingTestEntity").queryOf<EncodingTestEntity>().runOn(ctx)
    verify(res.get(0), EncodingTestEntity.regular)
    verify(res.get(1), EncodingTestEntity.regular)
  }

  "encodes and decodes nullables - nulls" {
    insert(EncodingTestEntity.empty).runOn(ctx)
    val res = Sql("SELECT * FROM EncodingTestEntity").queryOf<EncodingTestEntity>().runOn(ctx)
    verify(res.first(), EncodingTestEntity.empty)
  }

  "Encode/Decode Additional Java Types - regular" {
    Sql("DELETE FROM JavaTestEntity").action().runOn(ctx)
    insert(JavaTestEntity.regular).runOn(ctx)
    val actual = Sql("SELECT * FROM JavaTestEntity").queryOf<JavaTestEntity>().runOn(ctx).first()
    verify(actual, JavaTestEntity.regular)
  }

  "Encode/Decode Additional Java Types - empty" {
    Sql("DELETE FROM JavaTestEntity").action().runOn(ctx)
    insert(JavaTestEntity.empty).runOn(ctx)
    val actual = Sql("SELECT * FROM JavaTestEntity").queryOf<JavaTestEntity>().runOn(ctx).first()
    verify(actual, JavaTestEntity.empty)
  }

  "encodes and decodes KMP types" {
    Sql("DELETE FROM KmpTestEntity").action().runOn(ctx)
    insert(KmpTestEntity.regular).runOn(ctx)
    val actual = Sql("SELECT * FROM KmpTestEntity").queryOf<KmpTestEntity>().runOn(ctx).first()
    verify(actual, KmpTestEntity.regular)
  }

  "encodes and decodes time types" {
    Sql("DELETE FROM TimeEntity").action().runOn(ctx)
    val zid = ZoneId.systemDefault()
    val timeEntity = TimeEntity.make(zid)
    insert(timeEntity).runOn(ctx)
    val actual = Sql("SELECT * FROM TimeEntity").queryOf<TimeEntity>().runOn(ctx).first()
    assert(timeEntity == actual)
  }

  "Encode/Decode Other Time Types ordering" {
    Sql("DELETE FROM TimeEntity").action().runOn(ctx)

    val zid = ZoneId.systemDefault()
    val timeEntityA = TimeEntity.make(zid, TimeEntity.TimeEntityInput(2022, 1, 1, 1, 1, 1, 0))
    val timeEntityB = TimeEntity.make(zid, TimeEntity.TimeEntityInput(2022, 2, 2, 2, 2, 2, 0))

    // Importing extras messes around with the quto-quote, need to look into why
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
