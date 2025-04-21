@file:OptIn(TerpalSqlInternal::class)

package io.exoquery.sql

import io.exoquery.controller.ControllerBatchAction
import io.exoquery.controller.ControllerBatchActionReturning
import io.exoquery.controller.JsonValue
import io.exoquery.controller.TerpalSqlInternal
import io.exoquery.terpal.*
import io.exoquery.terpal.Messages
import kotlinx.serialization.serializer
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlin.reflect.KClass
import org.intellij.lang.annotations.Language
import kotlin.jvm.JvmName
import kotlinx.serialization.ExperimentalSerializationApi as SerApi

interface SqlFragment


data class Param<T>(val serializer: SerializationStrategy<T>, val cls: KClass<*>, val value: T?): SqlFragment {
  companion object {
    /**
     * Crete a contextual parameter. This relies database context having a specific encoder for the specified type.
     * In order to do that, extend your desired database context add the corresponding encoder to the "additionalEncoders" parameter.
     **/
    @OptIn(SerApi::class)
    inline fun <reified T: Any> contextual(value: T?): Param<T> = Param(ContextualSerializer(T::class), T::class, value)
    /** Alias for Param.contextual */
    inline fun <reified T: Any> ctx(value: T?): Param<T> = contextual(value)

    inline fun <reified T> withSerializer(value: T?, serializer: SerializationStrategy<T>): Param<T> = Param(serializer, T::class, value)
    inline fun <reified T> withSerializer(value: T?): Param<T> = Param(serializer<T>(), T::class, value)

    /** Alias for Param.withSerializer */
    inline fun <reified T> withSer(value: T?, serializer: SerializationStrategy<T>): Param<T> = withSerializer(value, serializer)
    inline fun <reified T> withSer(value: T?): Param<T> = withSerializer(value)

    inline fun <reified T> json(value: T?): Param<JsonValue<T>> = withSerializer(value?.let { JsonValue(it) })

    operator fun invoke(value: String?): Param<String> = Param(String.serializer(), String::class, value)
    operator fun invoke(value: Int?): Param<Int> = Param(Int.serializer(), Int::class, value)
    operator fun invoke(value: Long?): Param<Long> = Param(Long.serializer(), Long::class, value)
    operator fun invoke(value: Short?): Param<Short> = Param(Short.serializer(), Short::class, value)
    operator fun invoke(value: Byte?): Param<Byte> = Param(Byte.serializer(), Byte::class, value)
    operator fun invoke(value: Float?): Param<Float> = Param(Float.serializer(), Float::class, value)
    operator fun invoke(value: Double?): Param<Double> = Param(Double.serializer(), Double::class, value)
    operator fun invoke(value: Boolean?): Param<Boolean> = Param(Boolean.serializer(), Boolean::class, value)
    operator fun invoke(value: ByteArray?): Param<ByteArray> = Param(serializer<ByteArray>(), ByteArray::class, value)

    @OptIn(SerApi::class) operator fun invoke(value: LocalDate?): Param<LocalDate> = Param(ContextualSerializer(LocalDate::class), LocalDate::class, value)
    @OptIn(SerApi::class) operator fun invoke(value: LocalTime?): Param<LocalTime> = Param(ContextualSerializer(LocalTime::class), LocalTime::class, value)
    @OptIn(SerApi::class) operator fun invoke(value: LocalDateTime?): Param<LocalDateTime> = Param(ContextualSerializer(LocalDateTime::class), LocalDateTime::class, value)
    @OptIn(SerApi::class) operator fun invoke(value: Instant?): Param<Instant> = Param(ContextualSerializer(Instant::class), Instant::class, value)
  }
}

data class Params<T> @PublishedApi internal constructor (val serializer: SerializationStrategy<T>, val cls: KClass<*>, val values: List<T>): SqlFragment {
  fun toParamList(): List<Param<T>> = values?.map { Param(serializer, cls, it) } ?: emptyList()

  companion object {
    @OptIn(SerApi::class)
    inline fun <reified T: Any> contextual(values: List<T>): Params<T> = Params(ContextualSerializer(T::class), T::class, values)
    /** Alias for Param.contextual */
    inline fun <reified T: Any> ctx(values: List<T>): Params<T> = Params.contextual(values)

    inline fun <reified T> withSerializer(values: List<T>, serializer: SerializationStrategy<T>): Params<T> = Params(serializer, T::class, values)
    inline fun <reified T> withSerializer(values: List<T>): Params<T> = Params(serializer<T>(), T::class, values)

    /** Alias for Param.withSerializer */
    inline fun <reified T> withSer(values: List<T>, serializer: SerializationStrategy<T>): Params<T> = withSerializer<T>(values, serializer = serializer)
    inline fun <reified T> withSer(values: List<T>): Params<T> = withSerializer(values)

    inline fun empty(): Params<String> = Params(serializer<String>(), Nothing::class, emptyList())

    operator fun invoke(vararg values: String): Params<String> = Params(String.serializer(), String::class, values.asList())
    operator fun invoke(vararg values: Int): Params<Int> = Params(Int.serializer(), Int::class, values.asList())
    operator fun invoke(vararg values: Long): Params<Long> = Params(Long.serializer(), Long::class, values.asList())
    operator fun invoke(vararg values: Short): Params<Short> = Params(Short.serializer(), Short::class, values.asList())
    operator fun invoke(vararg values: Byte): Params<Byte> = Params(Byte.serializer(), Byte::class, values.asList())
    operator fun invoke(vararg values: Float): Params<Float> = Params(Float.serializer(), Float::class, values.asList())
    operator fun invoke(vararg values: Double): Params<Double> = Params(Double.serializer(), Double::class, values.asList())
    operator fun invoke(vararg values: Boolean): Params<Boolean> = Params(Boolean.serializer(), Boolean::class, values.asList())
    operator fun invoke(vararg values: ByteArray): Params<ByteArray> = Params(serializer<ByteArray>(), ByteArray::class, values.asList())

    @OptIn(SerApi::class) operator fun invoke(vararg values: LocalDate): Params<LocalDate> = Params(ContextualSerializer(LocalDate::class), LocalDate::class, values.asList())
    @OptIn(SerApi::class) operator fun invoke(vararg values: LocalTime): Params<LocalTime> = Params(ContextualSerializer(LocalTime::class), LocalTime::class, values.asList())
    @OptIn(SerApi::class) operator fun invoke(vararg values: LocalDateTime): Params<LocalDateTime> = Params(ContextualSerializer(LocalDateTime::class), LocalDateTime::class, values.asList())
    @OptIn(SerApi::class) operator fun invoke(vararg values: Instant): Params<Instant> = Params(ContextualSerializer(Instant::class), Instant::class, values.asList())

    @JvmName("StringList")
    fun list(values: List<String>): Params<String> = Params(String.serializer(), String::class, values)
    @JvmName("IntList")
    fun list(values: List<Int>): Params<Int> = Params(Int.serializer(), Int::class, values)
    @JvmName("LongList")
    fun list(values: List<Long>): Params<Long> = Params(Long.serializer(), Long::class, values)
    @JvmName("ShortList")
    fun list(values: List<Short>): Params<Short> = Params(Short.serializer(), Short::class, values)
    @JvmName("ByteList")
    fun list(values: List<Byte>): Params<Byte> = Params(Byte.serializer(), Byte::class, values)
    @JvmName("FloatList")
    fun list(values: List<Float>): Params<Float> = Params(Float.serializer(), Float::class, values)
    @JvmName("DoubleList")
    fun list(values: List<Double>): Params<Double> = Params(Double.serializer(), Double::class, values)
    @JvmName("BooleanList")
    fun list(values: List<Boolean>): Params<Boolean> = Params(Boolean.serializer(), Boolean::class, values)
    @JvmName("ByteArrayList")
    fun list(values: List<ByteArray>): Params<ByteArray> = Params(serializer<ByteArray>(), ByteArray::class, values)

    @JvmName("LocalDateList") @OptIn(SerApi::class) fun list(values: List<LocalDate>): Params<LocalDate> = Params(ContextualSerializer(LocalDate::class), LocalDate::class, values)
    @JvmName("LocalTimeList") @OptIn(SerApi::class) fun list(values: List<LocalTime>): Params<LocalTime> = Params(ContextualSerializer(LocalTime::class), LocalTime::class, values)
    @JvmName("LocalDateTime") @OptIn(SerApi::class) fun list(values: List<LocalDateTime>): Params<LocalDateTime> = Params(ContextualSerializer(LocalDateTime::class), LocalDateTime::class, values)
    @JvmName("Instant") @OptIn(SerApi::class) fun list(values: List<Instant>): Params<Instant> = Params(ContextualSerializer(Instant::class), Instant::class, values)
  }
}



class SqlBatchCallWithValues<A: Any>(protected val batch: SqlBatchCall<A>, protected val values: Sequence<A>) {
  // Note that we don't actually care about the value of element of the batch anymore
  // because the parameters have been prepared and it just needs to be excuted
  // We only need type-data when there is a value returned
  fun action(): ControllerBatchAction {
    val sql = batch.parts.joinToString("?")
    val paramSeq = values.map { batch.params(it).map { it.toStatementParam() } }
    return ControllerBatchAction(sql, paramSeq)
  }

  fun batchCallValues() = values
  fun batchCall() = batch

  inline fun <reified T> actionReturning(vararg returningColumns: String): ControllerBatchActionReturning<T> {
    val sql = batchCall().parts.joinToString("?")
    val paramSeq = batchCallValues().map { batchCall().params(it).map { it.toStatementParam() } }
    val resultMaker = serializer<T>()
    return ControllerBatchActionReturning.Row(sql, paramSeq, resultMaker, returningColumns.toList())
  }

  fun actionReturningId(): ControllerBatchActionReturning<Long> {
    val sql = batchCall().parts.joinToString("?")
    val paramSeq = batchCallValues().map { batchCall().params(it).map { it.toStatementParam() } }
    val resultMaker = serializer<Long>()
    return ControllerBatchActionReturning.Id(sql, paramSeq, resultMaker)
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

@TerpalSqlInternal
abstract class SqlBase: InterpolatorWithWrapper<SqlFragment, Statement> {
  operator fun invoke(@Language("SQL") string: String): Statement = Messages.throwPluginNotExecuted()

  @InterpolatorBackend
  fun interpolate(parts: () -> List<String>, params: () -> List<SqlFragment>): Statement {
    val partsList = parts().map { IR.Part(it) }
    val paramsList = params().map {
      when (it) {
        is Param<*> -> IR.Param(it)
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
  override fun inlined(value: String?): Param<*> =
    throw IllegalArgumentException("The `inline` function is not yet supported in terpal-sql.")

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
  override fun inlined(value: String?): SqlFragment =
    throw IllegalArgumentException("The `inline` function is not yet supported in terpal-sql.")

  override fun wrap(value: String?): SqlFragment = Param(value)
  override fun wrap(value: Int?): SqlFragment = Param(value)
  override fun wrap(value: Long?): SqlFragment = Param(value)
  override fun wrap(value: Short?): SqlFragment = Param(value)
  override fun wrap(value: Byte?): SqlFragment = Param(value)
  override fun wrap(value: Float?): SqlFragment = Param(value)
  override fun wrap(value: Double?): SqlFragment = Param(value)
  override fun wrap(value: Boolean?): SqlFragment = Param(value)
  fun wrap(value: ByteArray?): SqlFragment = Param(value)

  fun wrap(value: LocalDate?): SqlFragment = Param(value)
  fun wrap(value: LocalTime?): SqlFragment = Param(value)
  fun wrap(value: LocalDateTime?): SqlFragment = Param(value)
  fun wrap(value: Instant?): SqlFragment = Param(value)
}

abstract class SqlCommonBatchBase(): SqlBatchBase() {
  fun <V> wrap(value: V, cls: KClass<*>): SqlFragment =
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
