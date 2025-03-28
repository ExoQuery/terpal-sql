package io.exoquery.sql

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

@OptIn(TerpalSqlInternal::class)
data class Statement(val ir: IR.Splice): SqlFragment {
  operator fun plus(other: Statement) = Statement(IR.Splice(listOf(IR.Part.Empty, IR.Part.Empty, IR.Part.Empty), listOf(this.ir, other.ir)))

  data class QueryData(val sql: String, val params: List<Param<*>>)

  companion object {
    fun constructQuery(ir: IR.Splice): QueryData {
      // At this point we should only have Parts and Params. Otherwise there's some kind of error
      val flatIr = ir.flatten()
      val parts = flatIr.parts
      val params = flatIr.params.map {
        when (it) {
          is IR.Param -> it.value
          else -> throw IllegalStateException("Unexpected IR type in params: $it.\nParams: ${flatIr.params}")
        }
      }

      if (parts.size != params.size + 1)
        throw IllegalStateException(
          """|Mismatched parts (${parts.size})  and params (${params.size}) in query:
             |Parts: ${parts.map { it.value }}
             |Params: ${params.map { it.value }}
        """.trimMargin())

      return QueryData(parts.map { it.value }.joinToString("?"), params)
    }
  }

  inline fun <reified T> queryOf(): Query<T> {
    val (sql, params) = constructQuery(ir)
    val resultMaker = serializer<T>()
    return Query(sql, params, resultMaker)
  }

  fun <T> queryOf(serializer: KSerializer<T>): Query<T> {
    val (sql, params) = constructQuery(ir)
    return Query(sql, params, serializer)
  }

  fun action(): Action {
    val (sql, params) = constructQuery(ir)
    return Action(sql, params)
  }

  inline fun <reified T> actionReturning(vararg returningColumns: String): ActionReturning<T> {
    val (sql, params) = constructQuery(ir)
    val resultMaker = serializer<T>()
    return ActionReturningRow(sql, params, resultMaker, returningColumns.toList())
  }

  /**
   * If you need to return more than one column, use [actionReturning] instead.
   *
   * Most database drivers have a simplified interface for returning a single id from an insert statement,
   * even when more sophisticated mechanisms are also available (e.g. RETURNING in Postgres, OUTPUT in SQL Server, etc...).
   * This method is for that specific case. As such, it will typically use mechanisms such as `getGeneratedKeys`
   * (together with PreparedStatement.RETURN_GENERATED_KEYS) in JDBC. SQLite-specific APIs are typically
   * nice e.g. they provide a insert(...):Long method that returns the id.
   * In Postgres and Oracle you need to specify the output column name explicitly, if you do not
   * they will try to return all of the generated columns. That is what the idColumn parameter is for.
   * Otherwise you can ignore it.
   */
  inline fun actionReturningId(idColumn: String? = null): ActionReturningId {
    val (sql, params) = constructQuery(ir)
    val resultMaker = serializer<Long>()
    return ActionReturningId(sql, params, resultMaker, idColumn)
  }
}
