package io.exoquery.sql

import io.exoquery.terpal.InterpolatorBatchingWithWrapper
import io.exoquery.terpal.InterpolatorWithWrapper
import io.exoquery.terpal.Messages
import kotlinx.serialization.serializer
import io.exoquery.terpal.WrapFailureMessage
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.reflect.KClass

interface SqlFragment

class SqlBatchCallWithValues<A: Any>(protected val batch: SqlBatchCall<A>, protected val values: Sequence<A>) {
  // Note that we don't actually care about the value of element of the batch anymore
  // because the parameters have been prepared and it just needs to be excuted
  // We only need type-data when there is a value returned
  fun action(): BatchAction {
    val sql = batch.parts.joinToString("?")
    val paramSeq = values.map { batch.params(it) }
    return BatchAction(sql, paramSeq)
  }

  fun batchCallValues() = values
  fun batchCall() = batch

  inline fun <reified T> actionReturning(vararg returningColumns: String): BatchActionReturning<T> {
    val sql = batchCall().parts.joinToString("?")
    val paramSeq = batchCallValues().map { batchCall().params(it) }
    val resultMaker = serializer<T>()
    return BatchActionReturningRow(sql, paramSeq, resultMaker, returningColumns.toList())
  }

  fun actionReturningId(): BatchActionReturning<Long> {
    val sql = batchCall().parts.joinToString("?")
    val paramSeq = batchCallValues().map { batchCall().params(it) }
    val resultMaker = serializer<Long>()
    return BatchActionReturningId(sql, paramSeq, resultMaker)
  }
}

data class SqlBatchCall<T: Any>(val parts: List<String>, val params: (T) -> List<Param<T>>) {
  fun values(values: Sequence<T>) = SqlBatchCallWithValues(this, values)
  fun values(values: Iterable<T>) = SqlBatchCallWithValues(this, values.asSequence())
  fun values(vararg values: T) = SqlBatchCallWithValues(this, sequenceOf(*values))

  /*
  ----- Optimization -----
  actually what we ant the interface to be is List<(T) -> Fragment> where each param is an element of T
  need to change InterpolatorBatching macro to do that

  make the assumption that all dollar signs in batch queries are params,
  i.e. no Fragements so we don't actually need to flatten anything to make
  that work. Go through the List<(T) -> Fragement> once the macro is updated
  and create a List<Param<T>> from each List<(T) -> Fragment>
  that will give us our List<List<Param<T>> that we can use with the batch query
  }
   */
}

abstract class SqlBatchBase: InterpolatorBatchingWithWrapper<Param<*>> {
  override fun <A : Any> invoke(create: (A) -> String): SqlBatchCall<A> = Messages.throwPluginNotExecuted()
  @Suppress("UNCHECKED_CAST")
  override fun <A : Any> interpolate(parts: () -> List<String>, params: (A) -> List<Param<*>>): SqlBatchCall<A> =
    SqlBatchCall<A>(parts(), params as (A) -> List<Param<A>>)
}

abstract class SqlBase: InterpolatorWithWrapper<SqlFragment, Statement> {
  override fun interpolate(parts: () -> List<String>, params: () -> List<SqlFragment>): Statement {
    val partsList = parts().map { IR.Part(it) }
    val paramsList = params().map {
      when (it) {
        is Param<*> -> IR.Param(it)
        // if it's a statement need to splice everything we've seen in that statement here
        // including the params that we saw in order
        is Statement -> it.ir
        else -> throw IllegalArgumentException("Value $it was not a part or param")
      }
    }

    return Statement(IR.Splice(partsList, paramsList))
  }
}

@WrapFailureMessage(
  """For a datatype that does not have a wrap-function, use the Param(...) constructor to lift it into the proper type. You may
need specify a serializer for the type or (if it is contextual) ensure that it has a encoder in the `additionalEncoders` of the context."""
)
object Sql: SqlCommonBase()

@WrapFailureMessage(
  """For a datatype that does not have a wrap-function, use the Param(...) constructor to lift it into the proper type. You may
need specify a serializer for the type or (if it is contextual) ensure that it has a encoder in the `additionalEncoders` of the context."""
)
object SqlBatch: SqlCommonBatchBase() {
  override fun wrap(value: String?): Param<String> = Param(value)
  override fun wrap(value: Int?): Param<Int> = Param(value)
  override fun wrap(value: Long?): Param<Long> = Param(value)
  override fun wrap(value: Short?): Param<Short> = Param(value)
  override fun wrap(value: Byte?): Param<Byte> = Param(value)
  override fun wrap(value: Float?): Param<Float> = Param(value)
  override fun wrap(value: Double?): Param<Double> = Param(value)
  override fun wrap(value: Boolean?): Param<Boolean> = Param(value)
  fun wrap(value: ByteArray?): Param<ByteArray> = Param(value)

  fun wrap(value: LocalDate?): Param<LocalDate> = Param(value)
  fun wrap(value: LocalTime?): Param<LocalTime> = Param(value)
  fun wrap(value: LocalDateTime?): Param<LocalDateTime> = Param(value)
  fun wrap(value: Instant?): Param<Instant> = Param(value)
}


// The Jdbc Specific Sql implemenation which will use the Jdbc wrapping functions to auto-wrap things
abstract class SqlCommonBase(): SqlBase() {
  override fun wrap(value: String?): SqlFragment = Param(value)
  override fun wrap(value: Int?): SqlFragment = Param(value)
  override fun wrap(value: Long?): SqlFragment = Param(value)
  override fun wrap(value: Short?): SqlFragment = Param(value)
  override fun wrap(value: Byte?): SqlFragment = Param(value)
  override fun wrap(value: Float?): SqlFragment = Param(value)
  override fun wrap(value: Double?): SqlFragment = Param(value)
  override fun wrap(value: Boolean?): SqlFragment = Param(value)
  fun wrap(value: ByteArray?): SqlFragment = Param(value)

  fun wrap(value: LocalDate?): Param<LocalDate> = Param(value)
  fun wrap(value: LocalTime?): Param<LocalTime> = Param(value)
  fun wrap(value: LocalDateTime?): Param<LocalDateTime> = Param(value)
  fun wrap(value: Instant?): Param<Instant> = Param(value)
}

abstract class SqlCommonBatchBase(): SqlBatchBase() {
  // TODO Should check this at compile-time
  override fun <V> wrap(value: V, cls: KClass<*>): Param<*> =
    when (cls) {
      String::class -> Param(value as String)
      Int::class -> Param(value as Int)
      Long::class -> Param(value as Long)
      Short::class -> Param(value as Short)
      Byte::class -> Param(value as Byte)
      Float::class -> Param(value as Float)
      Double::class -> Param(value as Double)
      Boolean::class -> Param(value as Boolean)
      else ->
        throw IllegalArgumentException(
          """|Wrapped types are only allow to be the primitives: (String, Int, Long, Short, Byte, Float, Double, Boolean)
             |If you are attempint to splice one of these into a Sql string please use the Param(...) constructor on the value first
        """.trimMargin())
    }
}
