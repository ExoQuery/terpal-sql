package io.exoquery.r2dbc.postgres

import io.exoquery.controller.TerpalSqlUnsafe
import io.exoquery.controller.r2dbc.R2dbcController
import io.exoquery.controller.r2dbc.R2dbcControllers
import io.exoquery.controller.runActionsUnsafe
import io.exoquery.controller.runOn
import io.exoquery.r2dbc.Ex1_BatchInsertNormal
import io.exoquery.r2dbc.Ex2_BatchInsertMixed
import io.exoquery.r2dbc.Ex3_BatchReturnIds
import io.exoquery.r2dbc.Ex4_BatchReturnRecord
import io.exoquery.r2dbc.TestDatabasesR2dbc
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class BatchValuesSpec: FreeSpec ({

  val cf = TestDatabasesR2dbc.postgres
  val ctx: R2dbcController by lazy { R2dbcControllers.Postgres(connectionFactory = cf) }

  @OptIn(TerpalSqlUnsafe::class)
  suspend fun runActions(actions: String) = ctx.runActionsUnsafe(actions)

  beforeEach {
    runActions("TRUNCATE TABLE Product RESTART IDENTITY CASCADE")
  }

  "Ex 1 - Batch Insert Normal" {
    Ex1_BatchInsertNormal.op.runOn(ctx)
    Ex1_BatchInsertNormal.get.runOn(ctx) shouldBe Ex1_BatchInsertNormal.result
  }

  "Ex 2 - Batch Insert Mixed" {
    Ex2_BatchInsertMixed.op.runOn(ctx)
    Ex2_BatchInsertMixed.get.runOn(ctx) shouldBe Ex2_BatchInsertMixed.result
  }

  "Ex 3 - Batch Return Ids" {
    Ex3_BatchReturnIds.op.runOn(ctx) shouldBe Ex3_BatchReturnIds.opResult
    Ex3_BatchReturnIds.get.runOn(ctx) shouldBe Ex3_BatchReturnIds.result
  }

  "Ex 4 - Batch Return Record" {
    Ex4_BatchReturnRecord.op.runOn(ctx) shouldBe Ex4_BatchReturnRecord.opResult
    Ex4_BatchReturnRecord.get.runOn(ctx) shouldBe Ex4_BatchReturnRecord.result
  }
})
