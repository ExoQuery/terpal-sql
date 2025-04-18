package io.exoquery.sql.android.encodingdata

import io.exoquery.controller.android.AndroidEncodingConfig
import io.exoquery.controller.sqlite.SqliteBasicEncoding.StringEncoder
import java.math.BigDecimal
import io.exoquery.sql.encodingdata.EncodingTestEntity
import io.exoquery.sql.encodingdata.SerializeableTestType
import org.junit.Assert
import kotlin.test.assertEquals

val encodingConfig = AndroidEncodingConfig.Empty(additionalEncoders = setOf(StringEncoder.contramap { ett: SerializeableTestType -> ett.value }))

public infix fun BigDecimal.shouldBeEqualIgnoringScale(expected: BigDecimal) =
  this.shouldBeEqualIgnoringScaleNullable(expected)

public infix fun BigDecimal?.shouldBeEqualIgnoringScaleNullable(expected: BigDecimal?) =
  if (this == null && expected == null) Unit
  else if (this == null || expected == null) assertEquals(this, expected) // i.e. will always be false
  else
    if (this.compareTo(expected) != 0)
      Assert.fail("Expected <$expected>, actual <$this> is not same.") // otherwise they are both not null and we compare by scale
    else
      Unit
