package io.exoquery.controller.android

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteStatement
import io.exoquery.controller.CoroutineSession
import io.exoquery.controller.sqlite.Borrowed
import io.exoquery.controller.sqlite.DoublePoolSession
import io.exoquery.controller.sqlite.StatementCachingSession
import io.exoquery.controller.sqlite.TerpalSchema
import kotlinx.coroutines.*

object EmptyCallback : SupportSQLiteOpenHelper.Callback(1) {
  override fun onCreate(db: SupportSQLiteDatabase) = Unit
  override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

fun <T> TerpalSchema<T>.asSyncCallback(): SupportSQLiteOpenHelper.Callback {
  val schema = this@asSyncCallback
  return object : SupportSQLiteOpenHelper.Callback(schema.version.toInt()) {
    override fun onCreate(db: SupportSQLiteDatabase): Unit {
      val session: Connection =
        DoublePoolSession(
          Borrowed.dummy(StatementCachingSession<SupportSQLiteDatabase, SupportSQLiteStatement>(db, AndroidLruStatementCache(db, 1))),
          true
        )
      // Run the schema creation in a context. It is not enough to just create a session with this connection,
      // we need to actually do so that in the runActionScoped block it will know there is an existing connection
      // on the coroutine context and not attempt to create a new one.
      val ctx = AndroidDatabaseController.fromSingleSession(db)
      runBlocking(Dispatchers.Unconfined) {
        // Note that since this needs to be run on the caller thread (of the code that is calling db.writableDatabase,
        // adding `+ Dispatchers.IO` to this context will shift the context away from that and cause a deadlock.
        withContext(CoroutineSession(session, AndroidCoroutineContext)) {
          schema.create(ctx)
        }
      }
    }

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
      val ctx = AndroidDatabaseController.fromSingleSession(db)
      runBlocking { schema.migrate(ctx, oldVersion.toLong(), newVersion.toLong()) }
    }
  }
}
