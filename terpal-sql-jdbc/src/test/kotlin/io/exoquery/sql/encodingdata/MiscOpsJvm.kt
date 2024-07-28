package io.exoquery.sql.encodingdata

import io.exoquery.sql.jdbc.JdbcBasicEncoding.Companion.StringEncoder
import io.exoquery.sql.jdbc.JdbcEncodingConfig
import io.kotest.matchers.bigdecimal.shouldBeEqualIgnoringScale
import java.math.BigDecimal
import kotlin.test.assertEquals

val encodingConfig = JdbcEncodingConfig(setOf(StringEncoder.contramap { ett: SerializeableTestType -> ett.value }))

public infix fun BigDecimal?.shouldBeEqualIgnoringScaleNullable(expected: BigDecimal?) =
  if (this == null && expected == null) Unit
  else if (this == null || expected == null) assertEquals(this, expected) // i.e. will always be false
  else this.shouldBeEqualIgnoringScale(expected) // otherwise they are both not null and we compare by scale