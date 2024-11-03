package io.exoquery.sql

import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.ExperimentalSerializationApi as SerApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer
//import java.math.BigDecimal
import kotlinx.datetime.*
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

// Note that T can't extend Any because then T will not be allowed to be null when it is being decoded
// that is why we have KClass<*> and not KClass<T>

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
