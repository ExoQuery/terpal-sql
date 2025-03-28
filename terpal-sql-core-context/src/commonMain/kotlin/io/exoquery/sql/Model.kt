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
