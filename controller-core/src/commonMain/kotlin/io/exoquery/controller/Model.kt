@file:OptIn(TerpalSqlInternal::class)

package io.exoquery.controller

import kotlinx.serialization.*

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Retention(AnnotationRetention.BINARY)
// TODO this needs to have AnnotationTarget.PROPERTY and not AnnotationTarget.FIELD or AnnotationTarget.VALUE_PARAMETER or else it
//      will not be retrieveable with getElementAnnotations. See https://github.com/Kotlin/kotlinx.serialization/issues/1001 for more details.
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.TYPE)
annotation class SqlJsonValue

data class ControllerQuery<T>(val sql: String, val params: List<StatementParam<*>>, val resultMaker: KSerializer<T>)

sealed interface ActionVerb<T>

data class ControllerAction(val sql: String, val params: List<StatementParam<*>>): ActionVerb<Long>

sealed interface ControllerActionReturning<T>: ActionVerb<T> {
  val sql: String
  val params: List<StatementParam<*>>
  val resultMaker: KSerializer<T>
  val returningColumns: List<String>

  data class Row<T>(override val sql: String, override val params: List<StatementParam<*>>, override val resultMaker: KSerializer<T>, override val returningColumns: List<String>): ControllerActionReturning<T>
  data class Id<T>(override val sql: String, override val params: List<StatementParam<*>>, override val resultMaker: KSerializer<T>, override val returningColumns: List<String>): ControllerActionReturning<T> {
    companion object {
      operator fun invoke(sql: String, params: List<StatementParam<*>>, resultMaker: KSerializer<Long>, returningColumn: String? = null): ControllerActionReturning.Id<Long> {
        return ControllerActionReturning.Id(sql, params, resultMaker, listOfNotNull(returningColumn))
      }
    }
  }
}


sealed interface BatchVerb<T>

data class ControllerBatchAction(val sql: String, val params: Sequence<List<StatementParam<*>>>): BatchVerb<Long>

sealed interface ControllerBatchActionReturning<T>: BatchVerb<T> {
  val sql: String
  val params: Sequence<List<StatementParam<*>>>
  val resultMaker: KSerializer<T>
  val returningColumns: List<String>

  data class Row<T>(override val sql: String, override val params: Sequence<List<StatementParam<*>>>, override val resultMaker: KSerializer<T>, override val returningColumns: List<String>): ControllerBatchActionReturning<T>
  data class Id<T>(override val sql: String, override val params: Sequence<List<StatementParam<*>>>, override val resultMaker: KSerializer<T>, override val returningColumns: List<String>): ControllerBatchActionReturning<T> {
    companion object {
      operator fun invoke(sql: String, params: Sequence<List<StatementParam<*>>>, resultMaker: KSerializer<Long>, returningColumn: String? = null): ControllerBatchActionReturning.Id<Long> {
        return ControllerBatchActionReturning.Id(sql, params, resultMaker, listOfNotNull(returningColumn))
      }
    }
  }
}
