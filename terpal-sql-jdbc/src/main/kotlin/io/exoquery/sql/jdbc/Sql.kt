@file:OptIn(TerpalSqlInternal::class)

package io.exoquery.sql.jdbc

import io.exoquery.controller.TerpalSqlInternal
import io.exoquery.sql.*
import io.exoquery.terpal.InterpolatorFunction
import io.exoquery.terpal.InterpolatorWithWrapper
import io.exoquery.terpal.StrictType
import io.exoquery.terpal.WrapFailureMessage
import org.intellij.lang.annotations.Language
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.time.*
import java.time.LocalTime
import kotlin.reflect.KClass

@WrapFailureMessage(
"""For a datatype that does not have a wrap-function, use the Param(...) constructor to lift it into the proper type. You may
need specify a serializer for the type or (if it is contextual) ensure that it has a encoder in the `additionalEncoders` of the context."""
)
object SqlInterpolator: InterpolatorWithWrapper<SqlFragment, Statement>, SqlJdbcBase()

@Deprecated("Use io.exoquery.sql.Sql instead", ReplaceWith("io.exoquery.sql.Sql"))
@InterpolatorFunction<SqlInterpolator>(SqlInterpolator::class)
fun Sql(@Language("SQL") query: String): Statement = io.exoquery.terpal.Messages.throwPluginNotExecuted()

@WrapFailureMessage(
"""For a datatype that does not have a wrap-function, use the Param(...) constructor to lift it into the proper type. You may
need specify a serializer for the type or (if it is contextual) ensure that it has a encoder in the `additionalEncoders` of the context."""
)
object SqlBatch: SqlJdbcBatchBase() {
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
  fun wrap(value: BigDecimal?): Param<BigDecimal> = Param.contextual(value)
  fun wrap(value: ByteArray?): Param<ByteArray> = Param(value)

  // It's a bit crazy but all the java.sql.* types are a subtype of this
  // so we want it to only match a strict java.util.Date parameter
  @StrictType
  fun wrap(value: java.util.Date?): Param<java.util.Date> = Param.fromUtilDate(value)

  fun wrap(value: java.sql.Date?): Param<java.sql.Date> = Param.fromSqlDate(value)
  fun wrap(value: java.sql.Time?): Param<java.sql.Time> = Param(value)
  fun wrap(value: java.sql.Timestamp?): Param<java.sql.Timestamp> = Param(value)

  fun wrap(value: kotlinx.datetime.LocalDate?): Param<kotlinx.datetime.LocalDate> = Param(value)
  fun wrap(value: kotlinx.datetime.LocalTime?): Param<kotlinx.datetime.LocalTime> = Param(value)
  fun wrap(value: kotlinx.datetime.LocalDateTime?): Param<kotlinx.datetime.LocalDateTime> = Param(value)
  fun wrap(value: kotlinx.datetime.Instant?): Param<kotlinx.datetime.Instant> = Param(value)

  fun wrap(value: LocalDate?): Param<LocalDate> = Param.contextual(value)
  fun wrap(value: LocalTime?): Param<LocalTime> = Param.contextual(value)
  fun wrap(value: LocalDateTime?): Param<LocalDateTime> = Param.contextual(value)
  fun wrap(value: ZonedDateTime?): Param<ZonedDateTime> = Param.contextual(value)
  fun wrap(value: Instant?): Param<Instant> = Param.contextual(value)
  fun wrap(value: OffsetTime?): Param<OffsetTime> = Param.contextual(value)
  fun wrap(value: OffsetDateTime?): Param<OffsetDateTime> = Param.contextual(value)
}


// The Jdbc Specific Sql implemenation which will use the Jdbc wrapping functions to auto-wrap things
abstract class SqlJdbcBase(): SqlBase() {
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
  fun wrap(value: BigDecimal?): SqlFragment = Param.contextual(value)
  fun wrap(value: ByteArray?): SqlFragment = Param(value)

  // It's a bit crazy but all the java.sql.* types are a subtype of this
  // so we want it to only match a strict java.util.Date parameter
  @StrictType
  fun wrap(value: java.util.Date?): SqlFragment = Param.fromUtilDate(value)

  fun wrap(value: java.sql.Date?): SqlFragment = Param.fromSqlDate(value)
  fun wrap(value: java.sql.Time?): SqlFragment = Param(value)
  fun wrap(value: java.sql.Timestamp?): SqlFragment = Param(value)

  fun wrap(value: kotlinx.datetime.LocalDate?): Param<kotlinx.datetime.LocalDate> = Param(value)
  fun wrap(value: kotlinx.datetime.LocalTime?): Param<kotlinx.datetime.LocalTime> = Param(value)
  fun wrap(value: kotlinx.datetime.LocalDateTime?): Param<kotlinx.datetime.LocalDateTime> = Param(value)
  fun wrap(value: kotlinx.datetime.Instant?): Param<kotlinx.datetime.Instant> = Param(value)

  fun wrap(value: LocalDate?): SqlFragment = Param.contextual(value)
  fun wrap(value: LocalTime?): SqlFragment = Param.contextual(value)
  fun wrap(value: LocalDateTime?): SqlFragment = Param.contextual(value)
  fun wrap(value: ZonedDateTime?): SqlFragment = Param.contextual(value)
  fun wrap(value: Instant?): SqlFragment = Param.contextual(value)
  fun wrap(value: OffsetTime?): SqlFragment = Param.contextual(value)
  fun wrap(value: OffsetDateTime?): SqlFragment = Param.contextual(value)
}

abstract class SqlJdbcBatchBase(): SqlBatchBase() {
  fun <V> wrap(value: V, cls: KClass<*>): Param<*> =
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

