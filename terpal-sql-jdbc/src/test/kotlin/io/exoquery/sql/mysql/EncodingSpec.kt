package io.exoquery.sql.mysql

import io.exoquery.sql.*
import io.exoquery.sql.encodingdata.*
import io.exoquery.sql.jdbc.Sql
import io.exoquery.sql.jdbc.TerpalContext
import io.exoquery.sql.runOn
import io.kotest.core.spec.style.FreeSpec
import java.time.ZoneId

class EncodingSpec: FreeSpec({
  val ds = TestDatabases.mysql
  val ctx by lazy { TerpalContext.Mysql(ds, encodingConfig) }

  beforeEach {
    ds.run("DELETE FROM EncodingTestEntity")
  }

  "encodes and decodes nullables - not nulls" {
    ctx.run(insert(EncodingTestEntity.regular))
    val res = ctx.run(Sql("SELECT * FROM EncodingTestEntity").queryOf<EncodingTestEntity>())
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
    ctx.run(insert(JavaTestEntity.regular))
    val actual = ctx.run(Sql("SELECT * FROM JavaTestEntity").queryOf<JavaTestEntity>()).first()
    verify(actual, JavaTestEntity.regular)
  }

  "Encode/Decode Additional Java Types - empty" {
    Sql("DELETE FROM JavaTestEntity").action().runOn(ctx)
    ctx.run(insert(JavaTestEntity.empty))
    val actual = ctx.run(Sql("SELECT * FROM JavaTestEntity").queryOf<JavaTestEntity>()).first()
    verify(actual, JavaTestEntity.empty)
  }

  "Encode/Decode KMP Types" {
    Sql("DELETE FROM KmpTestEntity").action().runOn(ctx)
    ctx.run(insert(KmpTestEntity.regular))
    val actual = ctx.run(Sql("SELECT * FROM KmpTestEntity").queryOf<KmpTestEntity>()).first()
    verify(actual, KmpTestEntity.regular)
  }

  "Encode/Decode Other Time Types" {
    Sql("DELETE FROM TimeEntity").action().runOn(ctx)
    val zid = ZoneId.systemDefault()
    val timeEntity = TimeEntity.make(zid)
    ctx.run(insert(timeEntity))
    val actual = ctx.run(Sql("SELECT * FROM TimeEntity").queryOf<TimeEntity>()).first()
    assert(timeEntity == actual)
  }

  "Encode/Decode Other Time Types ordering" {
    Sql("DELETE FROM TimeEntity").action().runOn(ctx)

    val zid = ZoneId.systemDefault()
    val timeEntityA = TimeEntity.make(zid, TimeEntity.TimeEntityInput(2022, 1, 1, 1, 1, 1, 0))
    val timeEntityB = TimeEntity.make(zid, TimeEntity.TimeEntityInput(2022, 2, 2, 2, 2, 2, 0))

    // Importing extras messes around with the quto-quote, need to look into why
    ctx.run(insert(timeEntityA))
    ctx.run(insert(timeEntityB))

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
