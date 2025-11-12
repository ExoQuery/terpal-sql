package io.exoquery.controller.r2dbc

internal fun changePlaceholdersIn(sql: String, changeTo: (Int) -> String): String {
  // MSSQL R2DBC uses @1, @2... for placeholders.
  // Most other R2DBC drivers (e.g. MSSQL) use '?', so do not rewrite for them.
  val sb = StringBuilder()
  var paramIndex = 0
  var i = 0
  while (i < sql.length) {
    val c = sql[i]
    if (c == '?') {
      // Params are named like @Param0, @Param1, ... parameter
      // binding is indexed based. SqlServer R2DBC supports this.
      sb.append(changeTo(paramIndex))
      paramIndex++
      i++
    } else {
      sb.append(c)
      i++
    }
  }
  return sb.toString()
}
