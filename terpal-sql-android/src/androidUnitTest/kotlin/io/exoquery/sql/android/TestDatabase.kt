package io.exoquery.sql.android

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.exoquery.sql.BasicSchema
import org.robolectric.shadows.ShadowLog
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object TestDatabase {
  val databaseName = "terpal_test.db"

  object EmptyCallback: SupportSQLiteOpenHelper.Callback(1) {
    override fun onCreate(db: SupportSQLiteDatabase) {}
    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
  }

  val ctx by lazy {
    // NOTE any stdio output here seems to be swallowed by Robolectric and/or the CI test runner
    ShadowLog.stream = System.out
    System.setProperty("robolectric.logging","stdout")
    val callback = AndroidSqliteDriver.Callback(BasicSchema)
    TerpalAndroidContext.fromApplicationContext(databaseName, ApplicationProvider.getApplicationContext(), callback)
  }
}