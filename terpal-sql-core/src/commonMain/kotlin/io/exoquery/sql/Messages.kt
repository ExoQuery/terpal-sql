package io.exoquery.sql

object Messages {

fun <T> catchRethrowColumnInfoExtractError(f:() -> T): T =
  try {
    f()
  } catch (e: Exception) {
    throw IllegalStateException(
"""Could not extract column information from the row.
This is likely because you attempted to retrieve column information for a row that does not exist. This frequently
happens when you are use a database driver that expects a 1-based index for columns (e.g. JDBC), but the row your context
is using a 0-based index or vice-versa. Make sure the `startingIndex` parameter for your context e.g. JdbcDriver.startingIndex
is set correctly.
================
Error: ${e.message} 
""".trimMargin(), e)
  }
}