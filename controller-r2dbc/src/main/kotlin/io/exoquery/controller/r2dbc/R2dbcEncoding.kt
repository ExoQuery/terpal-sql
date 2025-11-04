package io.exoquery.controller.r2dbc

import io.exoquery.controller.SqlDecoder
import io.exoquery.controller.SqlEncoder

// Placeholders for R2DBC encoders/decoders. Concrete implementations can be added later per driver needs.
object R2dbcEncoders {
  val encoders: Set<SqlEncoder<Any, Any, out Any>> = emptySet()
}

object R2dbcDecoders {
  val decoders: Set<SqlDecoder<Any, Any, out Any>> = emptySet()
}
