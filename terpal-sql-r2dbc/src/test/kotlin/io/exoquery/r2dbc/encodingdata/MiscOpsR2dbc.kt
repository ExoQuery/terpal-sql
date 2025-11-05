package io.exoquery.r2dbc.encodingdata

import io.exoquery.controller.r2dbc.R2dbcEncoderAny
import io.exoquery.controller.r2dbc.R2dbcEncodingConfig
import io.exoquery.sql.encodingdata.SerializeableTestType
import io.kotest.matchers.bigdecimal.shouldBeEqualIgnoringScale
import org.junit.jupiter.api.Assertions.assertEquals
import java.math.BigDecimal

val encodingConfig = R2dbcEncodingConfig(
  setOf(
    R2dbcEncoderAny(0, SerializeableTestType::class) { ctx, v, i -> ctx.stmt.bind(i, v.value) }
  )
)

public infix fun BigDecimal?.shouldBeEqualIgnoringScaleNullable(expected: BigDecimal?) =
  if (this == null && expected == null) Unit
  else if (this == null || expected == null) assertEquals(this, expected)
  else this.shouldBeEqualIgnoringScale(expected)
