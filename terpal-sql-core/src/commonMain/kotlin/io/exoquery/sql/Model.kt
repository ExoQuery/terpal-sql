@file:OptIn(TerpalSqlInternal::class)

package io.exoquery.sql

import kotlinx.serialization.*

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Retention(AnnotationRetention.BINARY)
// TODO this needs to have AnnotationTarget.PROPERTY and not AnnotationTarget.FIELD or AnnotationTarget.VALUE_PARAMETER or else it
//      will not be retrieveable with getElementAnnotations. See https://github.com/Kotlin/kotlinx.serialization/issues/1001 for more details.
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.TYPE)
annotation class SqlJsonValue

@Serializable
data class JsonValue<T>(val value: T)

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

data class Query<T>(val sql: String, val params: List<Param<*>>, val resultMaker: KSerializer<T>)
data class Action(val sql: String, val params: List<Param<*>>)

sealed interface ActionReturning<T> {
  val sql: String
  val params: List<Param<*>>
  val resultMaker: KSerializer<T>
  val returningColumns: List<String>
}
data class ActionReturningRow<T>(override val sql: String, override val params: List<Param<*>>, override val resultMaker: KSerializer<T>, override val returningColumns: List<String>): ActionReturning<T>
data class ActionReturningId(override val sql: String, override val params: List<Param<*>>, override val resultMaker: KSerializer<Long>, val returningColumn: String? = null): ActionReturning<Long> {
  override val returningColumns: List<String> = listOfNotNull(returningColumn)
}

data class BatchAction(val sql: String, val params: Sequence<List<Param<*>>>)

sealed interface BatchActionReturning<T> {
  val sql: String
  val params: Sequence<List<Param<*>>>
  val resultMaker: KSerializer<T>
  val returningColumns: List<String>
}
data class BatchActionReturningRow<T>(override val sql: String, override val params: Sequence<List<Param<*>>>, override val resultMaker: KSerializer<T>, override val returningColumns: List<String>): BatchActionReturning<T>
data class BatchActionReturningId(override val sql: String, override val params: Sequence<List<Param<*>>>, override val resultMaker: KSerializer<Long>, val returningColumn: String? = null): BatchActionReturning<Long> {
  override val returningColumns: List<String> = listOfNotNull(returningColumn)
}
