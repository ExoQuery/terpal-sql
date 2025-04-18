package io.exoquery.sql.android

import io.exoquery.controller.runActions
import io.exoquery.sql.Sql
import io.exoquery.sql.android.encodingdata.TimeEntity
import io.exoquery.sql.android.encodingdata.insert
import io.exoquery.sql.encodingdata.EncodingTestEntity
import io.exoquery.sql.encodingdata.insertBatch
import kotlinx.coroutines.runBlocking
import java.time.ZoneId
import kotlin.invoke
import kotlin.test.BeforeTest

class EncodingSpec {
  val ctx = TestDatabase.ctx

  @BeforeTest
  fun setup() = runBlocking {
    ctx.runActions("DELETE FROM EncodingTestEntity")
  }

  fun `encodes and decodes nullables - not nulls`() = runBlocking {
    insert(EncodingTestEntity.regular).runOn(ctx)
    val res = Sql("SELECT * FROM EncodingTestEntity").queryOf<EncodingTestEntity>().runOn(ctx)
    verify(res.first(), EncodingTestEntity.regular)
  }

  fun `encodes and decodes batch`() {
    insertBatch(listOf(EncodingTestEntity.regular, EncodingTestEntity.regular)).runOn(ctx)
    val res = Sql("SELECT * FROM EncodingTestEntity").queryOf<EncodingTestEntity>().runOn(ctx)
    verify(res.get(0), EncodingTestEntity.regular)
    verify(res.get(1), EncodingTestEntity.regular)
  }

  fun `encodes and decodes nullables - nulls`() {
    insert(EncodingTestEntity.empty).runOn(ctx)
    val res = Sql("SELECT * FROM EncodingTestEntity").queryOf<EncodingTestEntity>().runOn(ctx)
    verify(res.first(), EncodingTestEntity.empty)
  }

  fun `EncodeDecode Additional Java Types - regular`() {
    Sql("DELETE FROM JavaTestEntity").action().runOn(ctx)
    insert(JavaTestEntity.regular).runOn(ctx)
    val actual = Sql("SELECT * FROM JavaTestEntity").queryOf<JavaTestEntity>().runOn(ctx).first()
    verify(actual, JavaTestEntity.regular)
  }

  fun `EncodeDecode Additional Java Types - empty`() {
    Sql("DELETE FROM JavaTestEntity").action().runOn(ctx)
    insert(JavaTestEntity.empty).runOn(ctx)
    val actual = Sql("SELECT * FROM JavaTestEntity").queryOf<JavaTestEntity>().runOn(ctx).first()
    verify(actual, JavaTestEntity.empty)
  }

  fun `EncodeDecode KMP Types`() {
    Sql("DELETE FROM KmpTestEntity").action().runOn(ctx)
    insert(KmpTestEntity.regular).runOn(ctx)
    val actual = Sql("SELECT * FROM KmpTestEntity").queryOf<KmpTestEntity>().runOn(ctx).first()
    verify(actual, KmpTestEntity.regular)
  }

  fun `EncodeDecode Other Time Types`() {
    Sql("DELETE FROM TimeEntity").action().runOn(ctx)
    val zid = ZoneId.systemDefault()
    val timeEntity = TimeEntity.make(zid)
    insert(timeEntity).runOn(ctx)
    val actual = Sql("SELECT * FROM TimeEntity").queryOf<TimeEntity>().runOn(ctx).first()
    assert(timeEntity == actual)
  }

  fun `EncodeDecode Other Time Types ordering`() {
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


}
