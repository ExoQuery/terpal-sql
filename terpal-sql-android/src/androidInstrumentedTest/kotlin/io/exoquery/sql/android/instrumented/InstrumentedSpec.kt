package io.exoquery.sql.android.instrumented

import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.exoquery.sql.android.TerpalAndroidDriver

interface InstrumentedSpec {
  fun createDriver(databaseName: String, schema: SqlSchema<QueryResult.Value<Unit>>) = run {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    TerpalAndroidDriver.fromApplicationContext(databaseName, appContext, AndroidSqliteDriver.Callback(schema))
  }
}