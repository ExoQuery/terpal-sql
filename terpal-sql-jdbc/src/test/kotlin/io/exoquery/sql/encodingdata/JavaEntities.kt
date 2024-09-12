package io.exoquery.sql.encodingdata

import io.exoquery.sql.Action
import io.exoquery.sql.Param
import io.exoquery.sql.jdbc.Sql
import io.kotest.matchers.bigdecimal.shouldBeEqualIgnoringScale
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.sql.Date
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

@Serializable
data class JavaTestEntity(
  @Contextual val bigDecimalMan: BigDecimal,
  @Contextual val javaUtilDateMan: java.util.Date,
  @Contextual val uuidMan: UUID,
  @Contextual val bigDecimalOpt: BigDecimal?,
  @Contextual val javaUtilDateOpt: java.util.Date?,
  @Contextual val uuidOpt: UUID?
) {
  companion object {
    val regular =
      JavaTestEntity(
        BigDecimal("1.1"),
        Date.from(LocalDateTime.of(2013, 11, 23, 0, 0, 0, 0).toInstant(ZoneOffset.UTC)),
        UUID.randomUUID(),
        BigDecimal("1.1"),
        Date.from(LocalDateTime.of(2013, 11, 23, 0, 0, 0, 0).toInstant(ZoneOffset.UTC)),
        UUID.randomUUID()
      )

    val empty =
      JavaTestEntity(
        BigDecimal.ZERO,
        Date(0),
        UUID(0, 0),
        null,
        null,
        null
      )
  }
}

fun insert(e: JavaTestEntity): Action {
  fun wrap(value: UUID?): Param<UUID> = Param.ctx(value)
  return Sql("INSERT INTO JavaTestEntity VALUES (${e.bigDecimalMan}, ${e.javaUtilDateMan}, ${wrap(e.uuidMan)}, ${e.bigDecimalOpt}, ${e.javaUtilDateOpt}, ${wrap(e.uuidOpt)})").action()
}

fun verify(e: JavaTestEntity, expected: JavaTestEntity) {
  e.bigDecimalMan shouldBeEqualIgnoringScale expected.bigDecimalMan
  e.javaUtilDateMan shouldBeEqual expected.javaUtilDateMan
  e.uuidMan shouldBeEqual expected.uuidMan
  e.bigDecimalOpt shouldBeEqualIgnoringScaleNullable expected.bigDecimalOpt
  e.javaUtilDateOpt shouldBeEqualNullable  expected.javaUtilDateOpt
  e.uuidOpt shouldBeEqualNullable expected.uuidOpt
}
