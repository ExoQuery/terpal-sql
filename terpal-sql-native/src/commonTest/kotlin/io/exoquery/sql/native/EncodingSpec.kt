package io.exoquery.sql.native

import io.exoquery.sql.Sql
import io.exoquery.sql.encodingdata.*
import io.exoquery.sql.native.TestDatabase.ctx
import io.exoquery.controller.runOn
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test

class EncodingSpec {
  @BeforeTest
  fun clearTables() {
    ctx.runRaw("DELETE FROM EncodingTestEntity")
  }

  @Test
  fun `encodes and decodes nullables - not nulls`() = runBlocking {
    ctx.run(insert(EncodingTestEntity.regular))
    val res = ctx.run(Sql("SELECT * FROM EncodingTestEntity").queryOf<EncodingTestEntity>())
    verify(res.first(), EncodingTestEntity.regular)
  }

  // Ignored, batch not supported on native
  @Ignore
  @Test
  fun `encodes and decodes batch`() = runBlocking {
    insertBatch(listOf(EncodingTestEntity.regular, EncodingTestEntity.regular)).runOn(ctx)
    val res = Sql("SELECT * FROM EncodingTestEntity").queryOf<EncodingTestEntity>().runOn(ctx)
    verify(res.get(0), EncodingTestEntity.regular)
    verify(res.get(1), EncodingTestEntity.regular)
  }

  @Test
  fun `encodes and decodes nullables - nulls`() = runBlocking {
    insert(EncodingTestEntity.empty).runOn(ctx)
    val res = Sql("SELECT * FROM EncodingTestEntity").queryOf<EncodingTestEntity>().runOn(ctx)
    verify(res.first(), EncodingTestEntity.empty)
  }

  @Test
  fun `"Encode Decode KMP Types"`() = runBlocking {
    Sql("DELETE FROM KmpTestEntity").action().runOn(ctx)
    ctx.run(insert(KmpTestEntity.regular))
    val actual = ctx.run(Sql("SELECT * FROM KmpTestEntity").queryOf<KmpTestEntity>()).first()
    verify(actual, KmpTestEntity.regular)
  }
}
