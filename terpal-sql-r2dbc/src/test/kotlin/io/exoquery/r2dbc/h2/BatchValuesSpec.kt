package io.exoquery.r2dbc.h2

import io.exoquery.controller.r2dbc.R2dbcController
import io.exoquery.controller.r2dbc.R2dbcControllers
import io.exoquery.controller.runActions
import io.exoquery.controller.runOn
import io.exoquery.r2dbc.Ex1_BatchInsertNormal
import io.exoquery.r2dbc.Ex2_BatchInsertMixed
import io.exoquery.r2dbc.Ex3_BatchReturnIds
import io.exoquery.r2dbc.Ex3_BatchReturnIdsExplicit
import io.exoquery.r2dbc.Ex4_BatchReturnRecord
import io.exoquery.r2dbc.TestDatabasesR2dbc
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class BatchValuesSpec: FreeSpec ({

  val cf = TestDatabasesR2dbc.h2
  val ctx: R2dbcController by lazy { R2dbcControllers.H2(connectionFactory = cf) }

  suspend fun runActions(actions: String) = ctx.runActions(actions)

  beforeEach {
    runActions("TRUNCATE TABLE Product; ALTER TABLE Product ALTER COLUMN id RESTART WITH 1;")
  }

  "Ex 1 - Batch Insert Normal" {
    Ex1_BatchInsertNormal.op.runOn(ctx)
    Ex1_BatchInsertNormal.get.runOn(ctx) shouldBe Ex1_BatchInsertNormal.result
  }

  "Ex 2 - Batch Insert Mixed" {
    Ex2_BatchInsertMixed.op.runOn(ctx)
    Ex2_BatchInsertMixed.get.runOn(ctx) shouldBe Ex2_BatchInsertMixed.result
  }

  "Ex 3 - Batch Return Ids Explicit" {
    Ex3_BatchReturnIdsExplicit.op.runOn(ctx) shouldBe Ex3_BatchReturnIdsExplicit.opResult
    Ex3_BatchReturnIdsExplicit.get.runOn(ctx) shouldBe Ex3_BatchReturnIdsExplicit.result
  }
})
