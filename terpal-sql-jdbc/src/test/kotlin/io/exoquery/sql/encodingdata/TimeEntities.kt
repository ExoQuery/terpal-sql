package io.exoquery.sql.encodingdata

import io.exoquery.controller.Action
import io.exoquery.sql.Sql
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.*

@Serializable
data class TimeEntity(
  @Contextual val sqlDate: java.sql.Date,                      // DATE
  @Contextual val sqlTime: java.sql.Time,                      // TIME
  @Contextual val sqlTimestamp: java.sql.Timestamp,            // DATETIME
  @Contextual val timeLocalDate: java.time.LocalDate,          // DATE
  @Contextual val timeLocalTime: java.time.LocalTime,          // TIME
  @Contextual val timeLocalDateTime: java.time.LocalDateTime,  // DATETIME
  @Contextual val timeZonedDateTime: java.time.ZonedDateTime,  // DATETIMEOFFSET
  @Contextual val timeInstant: java.time.Instant,              // DATETIMEOFFSET
  @Contextual val timeOffsetTime: java.time.OffsetTime,        // TIME
  @Contextual val timeOffsetDateTime: java.time.OffsetDateTime // DATETIMEOFFSET
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
    fun make(zoneIdRaw: ZoneId, timeEntity: TimeEntityInput = TimeEntityInput.default) = run {
      val zoneId = zoneIdRaw.normalized()
      // Millisecond precisions in SQL Server and many contexts are wrong so not using them
      val nowInstant = timeEntity.toLocalDate().atZone(zoneId).toInstant()
      val nowDateTime = LocalDateTime.ofInstant(nowInstant, zoneId)
      val nowDate = nowDateTime.toLocalDate()
      val nowTime = nowDateTime.toLocalTime()
      val nowZoned = ZonedDateTime.of(nowDateTime, zoneId)
      TimeEntity(
        java.sql.Date.valueOf(nowDate),
        java.sql.Time.valueOf(nowTime),
        java.sql.Timestamp.valueOf(nowDateTime),
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

fun insert(e: TimeEntity): Action {
  return Sql("INSERT INTO TimeEntity VALUES (${e.sqlDate}, ${e.sqlTime}, ${e.sqlTimestamp}, ${e.timeLocalDate}, ${e.timeLocalTime}, ${e.timeLocalDateTime}, ${e.timeZonedDateTime}, ${e.timeInstant}, ${e.timeOffsetTime}, ${e.timeOffsetDateTime})").action()
}
