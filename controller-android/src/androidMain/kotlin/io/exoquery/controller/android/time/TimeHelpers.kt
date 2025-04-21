package io.exoquery.controller.android.time

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

internal val dayZero = LocalDate.of(1970, 1, 1)
fun java.time.LocalTime.toSqlTime(zoneId: ZoneId) = java.sql.Time(this.atDate(dayZero).atZone(zoneId).toInstant().toEpochMilli())
fun java.time.LocalDate.toSqlDate(zoneId: ZoneId) = java.sql.Date(this.atStartOfDay(zoneId).toInstant().toEpochMilli())
fun java.time.LocalDateTime.toSqlTimestamp(zoneId: ZoneId) = java.sql.Timestamp(this.atZone(zoneId).toInstant().toEpochMilli())


//fun LocalDate.toSqlDate(): java.sql.Date = run {
//  val date = this
//  java.sql.Date(date.getYear() - 1900, date.getMonthValue() -1,date.getDayOfMonth())
//}
//
//fun LocalTime.toSqlTime(): java.sql.Time = run {
//  val time = this
//  java.sql.Time(time.getHour(), time.getMinute(), time.getSecond())
//}
//
//fun LocalDateTime.toSqlTimestamp(): java.sql.Timestamp = run {
//  val dateTime = this
//  java.sql.Timestamp(dateTime.getYear() - 1900,
//    dateTime.getMonthValue() - 1,
//    dateTime.getDayOfMonth(),
//    dateTime.getHour(),
//    dateTime.getMinute(),
//    dateTime.getSecond(),
//    dateTime.getNano()
//  )
//}
