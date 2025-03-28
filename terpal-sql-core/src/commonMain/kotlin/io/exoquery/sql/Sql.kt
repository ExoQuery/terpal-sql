@file:OptIn(TerpalSqlInternal::class)

package io.exoquery.sql

import io.exoquery.terpal.*
import io.exoquery.terpal.Messages
import kotlinx.serialization.serializer
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.reflect.KClass
import org.intellij.lang.annotations.Language

interface SqlFragment

data class ParamFragment<T>(val param: Param<T>): SqlFragment

class SqlBatchCallWithValues<A: Any>(protected val batch: SqlBatchCall<A>, protected val values: Sequence<A>) {
  // Note that we don't actually care about the value of element of the batch anymore
  // because the parameters have been prepared and it just needs to be excuted
  // We only need type-data when there is a value returned
  fun action(): BatchAction {
    val sql = batch.parts.joinToString("?")
    val paramSeq = values.map { batch.params(it).map { it.param } }
    return BatchAction(sql, paramSeq)
  }

  fun batchCallValues() = values
  fun batchCall() = batch

  inline fun <reified T> actionReturning(vararg returningColumns: String): BatchActionReturning<T> {
    val sql = batchCall().parts.joinToString("?")
    val paramSeq = batchCallValues().map { batchCall().params(it).map { it.param } }
    val resultMaker = serializer<T>()
    return BatchActionReturningRow(sql, paramSeq, resultMaker, returningColumns.toList())
  }

  fun actionReturningId(): BatchActionReturning<Long> {
    val sql = batchCall().parts.joinToString("?")
    val paramSeq = batchCallValues().map { batchCall().params(it).map { it.param } }
    val resultMaker = serializer<Long>()
    return BatchActionReturningId(sql, paramSeq, resultMaker)
  }
}

data class SqlBatchCall<T: Any>(val parts: List<String>, val params: (T) -> List<ParamFragment<T>>) {
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
  and create a List<ParamFragment<T>> from each List<(T) -> Fragment>
  that will give us our List<List<ParamFragment<T>> that we can use with the batch query
  }
   */
}

abstract class SqlBatchBase: InterpolatorBatchingWithWrapper<ParamFragment<*>> {
  override fun <A : Any> invoke(create: (A) -> String): SqlBatchCall<A> = Messages.throwPluginNotExecuted()
  @Suppress("UNCHECKED_CAST")
  override fun <A : Any> interpolate(parts: () -> List<String>, params: (A) -> List<ParamFragment<*>>): SqlBatchCall<A> =
    SqlBatchCall<A>(parts(), params as (A) -> List<ParamFragment<A>>)
}

@TerpalSqlInternal
abstract class SqlBase: InterpolatorWithWrapper<SqlFragment, Statement> {
  operator fun invoke(@Language("SQL") string: String): Statement = Messages.throwPluginNotExecuted()

  @InterpolatorBackend
  fun interpolate(parts: () -> List<String>, params: () -> List<SqlFragment>): Statement {
    val partsList = parts().map { IR.Part(it) }
    val paramsList = params().map {
      when (it) {
        is ParamFragment<*> -> IR.Param(it.param)
        is Params<*> -> IR.Params(it)
        // if it's a statement need to splice everything we've seen in that statement here
        // including the params that we saw in order
        is Statement -> it.ir
        else -> throw IllegalArgumentException("Value $it was not a part or param")
      }
    }

    return Statement(IR.Splice(partsList, paramsList))
  }
}

// Note that technically extending InterpolatorWithWrapper<SqlFragment, Statement> should not be needed
// and just extending SqlCommonBase() should be sufficient. Unfortunately due to issues
// with how Terpal-SQL traverses class hiearchcies looking for InterpolatorWithWrapper
// is incomplete it is necessary to do it explicitly. Need to look into this more.
@WrapFailureMessage(
  """For a datatype that does not have a wrap-function, use the Param(...) constructor to lift it into the proper type. You may
need specify a serializer for the type or (if it is contextual) ensure that it has a encoder in the `additionalEncoders` of the context."""
)
object SqlInterpolator: InterpolatorWithWrapper<SqlFragment, Statement>, SqlCommonBase()

@InterpolatorFunction<SqlInterpolator>(SqlInterpolator::class)
fun Sql(@Language("SQL") query: String): Statement = Messages.throwPluginNotExecuted()

@WrapFailureMessage(
  """For a datatype that does not have a wrap-function, use the Param(...) constructor to lift it into the proper type. You may
need specify a serializer for the type or (if it is contextual) ensure that it has a encoder in the `additionalEncoders` of the context."""
)
object SqlBatch: SqlCommonBatchBase() {
  override fun inlined(value: String?): ParamFragment<*> =
    throw IllegalArgumentException("The `inline` function is not yet supported in terpal-sql.")

  override fun wrap(value: String?): ParamFragment<String> = ParamFragment(Param(value))
  override fun wrap(value: Int?): ParamFragment<Int> = ParamFragment(Param(value))
  override fun wrap(value: Long?): ParamFragment<Long> = ParamFragment(Param(value))
  override fun wrap(value: Short?): ParamFragment<Short> = ParamFragment(Param(value))
  override fun wrap(value: Byte?): ParamFragment<Byte> = ParamFragment(Param(value))
  override fun wrap(value: Float?): ParamFragment<Float> = ParamFragment(Param(value))
  override fun wrap(value: Double?): ParamFragment<Double> = ParamFragment(Param(value))
  override fun wrap(value: Boolean?): ParamFragment<Boolean> = ParamFragment(Param(value))
  fun wrap(value: ByteArray?): SqlFragment = ParamFragment(Param(value))

  fun wrap(value: LocalDate?): SqlFragment = ParamFragment(Param(value))
  fun wrap(value: LocalTime?): SqlFragment = ParamFragment(Param(value))
  fun wrap(value: LocalDateTime?): SqlFragment = ParamFragment(Param(value))
  fun wrap(value: Instant?): SqlFragment = ParamFragment(Param(value))
}

// The Jdbc Specific Sql implemenation which will use the Jdbc wrapping functions to auto-wrap things
abstract class SqlCommonBase(): SqlBase() {
  override fun inlined(value: String?): SqlFragment =
    throw IllegalArgumentException("The `inline` function is not yet supported in terpal-sql.")

  override fun wrap(value: String?): SqlFragment = ParamFragment(Param(value))
  override fun wrap(value: Int?): SqlFragment = ParamFragment(Param(value))
  override fun wrap(value: Long?): SqlFragment = ParamFragment(Param(value))
  override fun wrap(value: Short?): SqlFragment = ParamFragment(Param(value))
  override fun wrap(value: Byte?): SqlFragment = ParamFragment(Param(value))
  override fun wrap(value: Float?): SqlFragment = ParamFragment(Param(value))
  override fun wrap(value: Double?): SqlFragment = ParamFragment(Param(value))
  override fun wrap(value: Boolean?): SqlFragment = ParamFragment(Param(value))
  fun wrap(value: ByteArray?): SqlFragment = ParamFragment(Param(value))

  fun wrap(value: LocalDate?): SqlFragment = ParamFragment(Param(value))
  fun wrap(value: LocalTime?): SqlFragment = ParamFragment(Param(value))
  fun wrap(value: LocalDateTime?): SqlFragment = ParamFragment(Param(value))
  fun wrap(value: Instant?): SqlFragment = ParamFragment(Param(value))
}

abstract class SqlCommonBatchBase(): SqlBatchBase() {
  fun <V> wrap(value: V, cls: KClass<*>): SqlFragment =
    when (cls) {
      String::class -> ParamFragment(Param(value as String))
      Int::class -> ParamFragment(Param(value as Int))
      Long::class -> ParamFragment(Param(value as Long))
      Short::class -> ParamFragment(Param(value as Short))
      Byte::class -> ParamFragment(Param(value as Byte))
      Float::class -> ParamFragment(Param(value as Float))
      Double::class -> ParamFragment(Param(value as Double))
      Boolean::class -> ParamFragment(Param(value as Boolean))
      else ->
        throw IllegalArgumentException(
          """|Wrapped types are only allow to be the primitives: (String, Int, Long, Short, Byte, Float, Double, Boolean)
             |If you are attempint to splice one of these into a Sql string please use the Param(...) constructor on the value first
        """.trimMargin())
    }
}
