package io.exoquery.sql.android.encodingdata

import io.exoquery.controller.Action
import io.exoquery.controller.android.time.toSqlDate
import io.exoquery.controller.android.time.toSqlTime
import io.exoquery.controller.android.time.toSqlTimestamp
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

    fun make(zoneIdRaw: ZoneId, timeEntity: TimeEntityInput = TimeEntityInput.default) = run {
      val zoneId = zoneIdRaw.normalized()
      // Millisecond precisions in SQL Server and many contexts are wrong so not using them
      val nowInstant = timeEntity.toLocalDate().atZone(zoneId).toInstant()
      val nowDateTime = LocalDateTime.ofInstant(nowInstant, zoneId)
      val nowDate = nowDateTime.toLocalDate()
      val nowTime = nowDateTime.toLocalTime()
      val nowZoned = ZonedDateTime.of(nowDateTime, zoneId)
      TimeEntity(
        nowDate.toSqlDate(zoneIdRaw),
        nowTime.toSqlTime(zoneIdRaw),
        nowDateTime.toSqlTimestamp(zoneIdRaw),
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
