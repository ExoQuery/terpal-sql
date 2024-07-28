package io.exoquery.sql

import kotlinx.serialization.ContextualSerializer
import java.math.BigDecimal
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import java.time.ZonedDateTime
import java.util.*
import java.time.*
import kotlinx.serialization.ExperimentalSerializationApi as SerApi

@OptIn(SerApi::class) operator fun Param.Companion.invoke(value: LocalDate?): Param<LocalDate> = Param(ContextualSerializer(LocalDate::class), LocalDate::class, value)
@OptIn(SerApi::class) operator fun Param.Companion.invoke(value: LocalTime?): Param<LocalTime> = Param(ContextualSerializer(LocalTime::class), LocalTime::class, value)
@OptIn(SerApi::class) operator fun Param.Companion.invoke(value: LocalDateTime?): Param<LocalDateTime> = Param(ContextualSerializer(LocalDateTime::class), LocalDateTime::class, value)
@OptIn(SerApi::class) operator fun Param.Companion.invoke(value: Instant?): Param<Instant> = Param(ContextualSerializer(Instant::class), Instant::class, value)

@OptIn(SerApi::class) operator fun Param.Companion.invoke(value: java.util.Date?): Param<java.util.Date> = Param(ContextualSerializer(java.util.Date::class), java.util.Date::class, value)
@OptIn(SerApi::class) operator fun Param.Companion.invoke(value: java.sql.Date?): Param<Date> = Param(ContextualSerializer(Date::class), Date::class, value)
@OptIn(SerApi::class) operator fun Param.Companion.invoke(value: java.sql.Time?): Param<Time> = Param(ContextualSerializer(Time::class), Time::class, value)
@OptIn(SerApi::class) operator fun Param.Companion.invoke(value: java.sql.Timestamp?): Param<Timestamp> = Param(ContextualSerializer(Timestamp::class), Timestamp::class, value)
@OptIn(SerApi::class) operator fun Param.Companion.invoke(value: BigDecimal?): Param<BigDecimal> = Param(ContextualSerializer(BigDecimal::class), BigDecimal::class, value)
@OptIn(SerApi::class) operator fun Param.Companion.invoke(value: ZonedDateTime?): Param<ZonedDateTime> = Param(ContextualSerializer(ZonedDateTime::class), ZonedDateTime::class, value)
@OptIn(SerApi::class) operator fun Param.Companion.invoke(value: OffsetTime?): Param<OffsetTime> = Param(ContextualSerializer(OffsetTime::class), OffsetTime::class, value)
@OptIn(SerApi::class) operator fun Param.Companion.invoke(value: OffsetDateTime?): Param<OffsetDateTime> = Param(ContextualSerializer(OffsetDateTime::class), OffsetDateTime::class, value)
