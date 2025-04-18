package io.exoquery.sql

import io.exoquery.sql.Param
import io.exoquery.terpal.StrictType
import java.math.BigDecimal
import java.time.*

fun SqlInterpolator.wrap(value: BigDecimal?): Param<BigDecimal> = Param.contextual(value)
fun SqlInterpolator.wrap(value: ByteArray?): Param<ByteArray> = Param(value)

// It's a bit crazy but all the java.sql.* types are a subtype of this
// so we want it to only match a strict java.util.Date parameter
@StrictType
fun SqlInterpolator.wrap(value: java.util.Date?): Param<java.util.Date> = Param.fromUtilDate(value)

fun SqlInterpolator.wrap(value: java.sql.Date?): Param<java.sql.Date> = Param.fromSqlDate(value)
fun SqlInterpolator.wrap(value: java.sql.Time?): Param<java.sql.Time> = Param(value)
fun SqlInterpolator.wrap(value: java.sql.Timestamp?): Param<java.sql.Timestamp> = Param(value)

fun SqlInterpolator.wrap(value: kotlinx.datetime.LocalDate?): Param<kotlinx.datetime.LocalDate> = Param(value)
fun SqlInterpolator.wrap(value: kotlinx.datetime.LocalTime?): Param<kotlinx.datetime.LocalTime> = Param(value)
fun SqlInterpolator.wrap(value: kotlinx.datetime.LocalDateTime?): Param<kotlinx.datetime.LocalDateTime> = Param(value)
fun SqlInterpolator.wrap(value: kotlinx.datetime.Instant?): Param<kotlinx.datetime.Instant> = Param(value)

fun SqlInterpolator.wrap(value: LocalDate?): Param<LocalDate> = Param.contextual(value)
fun SqlInterpolator.wrap(value: LocalTime?): Param<LocalTime> = Param.contextual(value)
fun SqlInterpolator.wrap(value: LocalDateTime?): Param<LocalDateTime> = Param.contextual(value)
fun SqlInterpolator.wrap(value: ZonedDateTime?): Param<ZonedDateTime> = Param.contextual(value)
fun SqlInterpolator.wrap(value: Instant?): Param<Instant> = Param.contextual(value)
fun SqlInterpolator.wrap(value: OffsetTime?): Param<OffsetTime> = Param.contextual(value)
fun SqlInterpolator.wrap(value: OffsetDateTime?): Param<OffsetDateTime> = Param.contextual(value)
