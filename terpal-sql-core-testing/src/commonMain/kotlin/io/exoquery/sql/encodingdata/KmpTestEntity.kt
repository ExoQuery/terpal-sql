package io.exoquery.sql.encodingdata

import io.exoquery.sql.Sql
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class KmpTestEntity(
  @Contextual val sqlDate: kotlinx.datetime.LocalDate,
  @Contextual val sqlTimestamp: kotlinx.datetime.LocalTime,
  @Contextual val sqlTime: kotlinx.datetime.LocalDateTime,
  @Contextual val timeLocalDate: kotlinx.datetime.Instant,

  @Contextual val sqlDateOpt: kotlinx.datetime.LocalDate?,
  @Contextual val sqlTimestampOpt: kotlinx.datetime.LocalTime?,
  @Contextual val sqlTimeOpt: kotlinx.datetime.LocalDateTime?,
  @Contextual val timeLocalDateOpt: kotlinx.datetime.Instant?
) {
  companion object {
    val regular =
      KmpTestEntity(
        kotlinx.datetime.LocalDate(2013, 11, 23),
        kotlinx.datetime.LocalTime(1, 2, 3, 0),
        kotlinx.datetime.LocalDateTime(2013, 11, 23, 1, 2, 3, 0),
        kotlinx.datetime.Instant.fromEpochSeconds(100),
        kotlinx.datetime.LocalDate(2013, 11, 23),
        kotlinx.datetime.LocalTime(1, 2, 3, 0),
        kotlinx.datetime.LocalDateTime(2013, 11, 23, 1, 2, 3, 0),
        kotlinx.datetime.Instant.fromEpochSeconds(100)
      )

    val empty =
      KmpTestEntity(
        kotlinx.datetime.LocalDate(1970, 1, 1),
        kotlinx.datetime.LocalTime(0, 0, 0, 0),
        kotlinx.datetime.LocalDateTime(1970, 1, 1, 0, 0, 0, 0),
        kotlinx.datetime.Instant.fromEpochSeconds(0),
        null,
        null,
        null,
        null
      )
  }
}

fun insert(e: KmpTestEntity) =
  Sql("INSERT INTO KmpTestEntity VALUES (${e.sqlDate}, ${e.sqlTimestamp}, ${e.sqlTime}, ${e.timeLocalDate}, ${e.sqlDateOpt}, ${e.sqlTimestampOpt}, ${e.sqlTimeOpt}, ${e.timeLocalDateOpt})").action()

fun verify(e: KmpTestEntity, expected: KmpTestEntity) {
  e.sqlDate shouldBeEqual expected.sqlDate
  e.sqlTimestamp shouldBeEqual expected.sqlTimestamp
  e.sqlTime shouldBeEqual expected.sqlTime
  e.timeLocalDate shouldBeEqual expected.timeLocalDate
  e.sqlDateOpt shouldBeEqualNullable expected.sqlDateOpt
  e.sqlTimestampOpt shouldBeEqualNullable expected.sqlTimestampOpt
  e.sqlTimeOpt shouldBeEqualNullable expected.sqlTimeOpt
  e.timeLocalDateOpt shouldBeEqualNullable expected.timeLocalDateOpt
}
