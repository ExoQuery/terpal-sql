package io.exoquery.sql.android

import io.exoquery.controller.runActions
import io.exoquery.controller.runOn
import io.exoquery.sql.Sql
import io.exoquery.sql.android.encodingdata.JavaTestEntity
import io.exoquery.sql.android.encodingdata.TimeEntity
import io.exoquery.sql.android.encodingdata.insert
import io.exoquery.sql.android.encodingdata.insertTimeEntity
import io.exoquery.sql.encodingdata.insert
import io.exoquery.sql.encodingdata.EncodingTestEntity
import io.exoquery.sql.encodingdata.KmpTestEntity
import io.exoquery.sql.encodingdata.insertBatch
import io.exoquery.sql.encodingdata.verify
import kotlinx.coroutines.runBlocking
import java.time.ZoneId
import kotlin.test.BeforeTest
import io.exoquery.sql.android.encodingdata.verify
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class EncodingSpec {
  val ctx = TestDatabase.ctx

  @BeforeTest
  fun setup(): Unit = runBlocking {
    ctx.runActions("DELETE FROM EncodingTestEntity")
  }

  @Test
  fun `encodes and decodes nullables - not nulls`() = runBlocking {
    insert(EncodingTestEntity.regular).runOn(ctx)
    val res = Sql("SELECT * FROM EncodingTestEntity").queryOf<EncodingTestEntity>().runOn(ctx)
    verify(res.first(), EncodingTestEntity.regular)
  }

  // Not supported in android
  //@Test
  //fun `encodes and decodes batch`() = runBlocking {
  //  insertBatch(listOf(EncodingTestEntity.regular, EncodingTestEntity.regular)).runOn(ctx)
  //  val res = Sql("SELECT * FROM EncodingTestEntity").queryOf<EncodingTestEntity>().runOn(ctx)
  //  verify(res.get(0), EncodingTestEntity.regular)
  //  verify(res.get(1), EncodingTestEntity.regular)
  //}

  @Test
  fun `encodes and decodes nullables - nulls`() = runBlocking {
    insert(EncodingTestEntity.empty).runOn(ctx)
    val res = Sql("SELECT * FROM EncodingTestEntity").queryOf<EncodingTestEntity>().runOn(ctx)
    verify(res.first(), EncodingTestEntity.empty)
  }

  @Test
  fun `EncodeDecode Additional Java Types - regular`() = runBlocking {
    Sql("DELETE FROM JavaTestEntity").action().runOn(ctx)
    insert(JavaTestEntity.regular).runOn(ctx)
    val actual = Sql("SELECT * FROM JavaTestEntity").queryOf<JavaTestEntity>().runOn(ctx).first()
    verify(actual, JavaTestEntity.regular)
  }

  @Test
  fun `EncodeDecode Additional Java Types - empty`() = runBlocking {
    Sql("DELETE FROM JavaTestEntity").action().runOn(ctx)
    insert(JavaTestEntity.empty).runOn(ctx)
    val actual = Sql("SELECT * FROM JavaTestEntity").queryOf<JavaTestEntity>().runOn(ctx).first()
    verify(actual, JavaTestEntity.empty)
  }

  @Test
  fun `EncodeDecode KMP Types`() = runBlocking {
    Sql("DELETE FROM KmpTestEntity").action().runOn(ctx)
    insert(KmpTestEntity.regular).runOn(ctx)
    val actual = Sql("SELECT * FROM KmpTestEntity").queryOf<KmpTestEntity>().runOn(ctx).first()
    verify(actual, KmpTestEntity.regular)
  }

  @Test
  fun `EncodeDecode Other Time Types`() = runBlocking {
    Sql("DELETE FROM TimeEntity").action().runOn(ctx)
    val zid = ZoneId.systemDefault()
    val timeEntity = TimeEntity.make(zid)
    insertTimeEntity(timeEntity).runOn(ctx)
    val actual = Sql("SELECT * FROM TimeEntity").queryOf<TimeEntity>().runOn(ctx).first()
    assert(timeEntity == actual)
  }

  @Test
  fun `EncodeDecode Other Time Types ordering`() = runBlocking {
    Sql("DELETE FROM TimeEntity").action().runOn(ctx)

    val zid = ZoneId.systemDefault()
    val timeEntityA = TimeEntity.make(zid, TimeEntity.TimeEntityInput(2022, 1, 1, 1, 1, 1, 0))
    val timeEntityB = TimeEntity.make(zid, TimeEntity.TimeEntityInput(2022, 2, 2, 2, 2, 2, 0))

    // Importing extras messes around with the quto-quote, need to look into why
    insertTimeEntity(timeEntityA).runOn(ctx)
    insertTimeEntity(timeEntityB).runOn(ctx)

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

    assertEquals(timeEntityB.sqlDate, actual.sqlDate)
    assertEquals(timeEntityB.sqlTime, actual.sqlTime)
    assertEquals(timeEntityB.sqlTimestamp, actual.sqlTimestamp)
    assertEquals(timeEntityB.timeLocalDate, actual.timeLocalDate)
    assertEquals(timeEntityB.timeLocalTime, actual.timeLocalTime)
    assertEquals(timeEntityB.timeLocalDateTime, actual.timeLocalDateTime)
    assertEquals(timeEntityB.timeZonedDateTime, actual.timeZonedDateTime)
    assertEquals(timeEntityB.timeInstant, actual.timeInstant)
    assertEquals(timeEntityB.timeOffsetTime, actual.timeOffsetTime)
    assertEquals(timeEntityB.timeOffsetDateTime, actual.timeOffsetDateTime)

    assert(timeEntityB == actual)
  }


}
