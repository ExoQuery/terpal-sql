package io.exoquery.sql.android.encodingdata

import io.exoquery.controller.Action
import io.exoquery.sql.Sql
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import java.time.*

@Serializable
data class TimeEntity(
  @Contextual val sqlDate: Date,                      // DATE
  @Contextual val sqlTime: Time,                      // TIME
  @Contextual val sqlTimestamp: Timestamp,            // DATETIME
  @Contextual val timeLocalDate: LocalDate,          // DATE
  @Contextual val timeLocalTime: LocalTime,          // TIME
  @Contextual val timeLocalDateTime: LocalDateTime,  // DATETIME
  @Contextual val timeZonedDateTime: ZonedDateTime,  // DATETIMEOFFSET
  @Contextual val timeInstant: Instant,              // DATETIMEOFFSET
  @Contextual val timeOffsetTime: OffsetTime,        // TIME
  @Contextual val timeOffsetDateTime: OffsetDateTime // DATETIMEOFFSET
) {
  override fun equals(other: Any?): Boolean =
    when (other) {
      is TimeEntity ->
        this.sqlDate == other.sqlDate &&
          this.sqlTime == other.sqlTime &&
          this.sqlTimestamp == other.sqlTimestamp &&
          this.timeLocalDate == other.timeLocalDate &&
          this.timeLocalTime == other.timeLocalTime &&
          this.timeLocalDateTime == other.timeLocalDateTime &&
          this.timeZonedDateTime.isEqual(other.timeZonedDateTime) &&
          this.timeInstant == other.timeInstant &&
          this.timeOffsetTime.isEqual(other.timeOffsetTime) &&
          this.timeOffsetDateTime.isEqual(other.timeOffsetDateTime)
      else -> false
    }

  data class TimeEntityInput(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int, val second: Int, val nano: Int) {
    fun toLocalDate() = LocalDateTime.of(year, month, day, hour, minute, second, nano)
    companion object {
      val default = TimeEntityInput(2022, 1, 2, 3, 4, 6, 0)
    }
  }

  companion object {
    fun LocalDate.toSqlDate(): java.sql.Date = run {
      val date = this
      java.sql.Date(date.getYear() - 1900, date.getMonthValue() -1,date.getDayOfMonth())
    }

    fun LocalTime.toSqlTime(): java.sql.Time = run {
      val time = this
      java.sql.Time(time.getHour(), time.getMinute(), time.getSecond())
    }

    fun LocalDateTime.toSqlTimestamp(): java.sql.Timestamp = run {
      val dateTime = this
      java.sql.Timestamp(dateTime.getYear() - 1900,
        dateTime.getMonthValue() - 1,
        dateTime.getDayOfMonth(),
        dateTime.getHour(),
        dateTime.getMinute(),
        dateTime.getSecond(),
        dateTime.getNano()
      )
    }

    fun make(zoneIdRaw: ZoneId, timeEntity: TimeEntityInput = TimeEntityInput.default) = run {
      val zoneId = zoneIdRaw.normalized()
      // Millisecond precisions in SQL Server and many contexts are wrong so not using them
      val nowInstant = timeEntity.toLocalDate().atZone(zoneId).toInstant()
      val nowDateTime = LocalDateTime.ofInstant(nowInstant, zoneId)
      val nowDate = nowDateTime.toLocalDate()
      val nowTime = nowDateTime.toLocalTime()
      val nowZoned = ZonedDateTime.of(nowDateTime, zoneId)
      TimeEntity(
        nowDate.toSqlDate(),
        nowTime.toSqlTime(),
        nowDateTime.toSqlTimestamp(),
        nowDate,
        nowTime,
        nowDateTime,
        nowZoned,
        nowInstant,
        OffsetTime.ofInstant(nowInstant, zoneId),
        OffsetDateTime.ofInstant(nowInstant, zoneId)
      )
    }
  }
}

fun insertTimeEntity(e: TimeEntity): Action {
  return Sql("INSERT INTO TimeEntity VALUES (${e.sqlDate}, ${e.sqlTime}, ${e.sqlTimestamp}, ${e.timeLocalDate}, ${e.timeLocalTime}, ${e.timeLocalDateTime}, ${e.timeZonedDateTime}, ${e.timeInstant}, ${e.timeOffsetTime}, ${e.timeOffsetDateTime})").action()
}
