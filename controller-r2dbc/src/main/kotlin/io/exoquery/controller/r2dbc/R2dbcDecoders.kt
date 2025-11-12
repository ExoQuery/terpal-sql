package io.exoquery.controller.r2dbc

import io.exoquery.controller.DecoderAny
import io.exoquery.controller.SqlDecoder
import io.r2dbc.spi.Connection
import io.r2dbc.spi.Row
import kotlin.reflect.KClass

class R2dbcDecoderAny<T: Any>(
  override val type: KClass<T>,
  override val f: (R2dbcDecodingContext, Int) -> T?
): DecoderAny<T, Connection, Row>(
    type,
    { index, row ->
      row.get(index) == null
    },
    f
  ) {
}


object R2dbcDecoders {
  @Suppress("UNCHECKED_CAST")
  val decoders: Set<SqlDecoder<Connection, Row, out Any>> = setOf(
    R2dbcBasicEncoding.BooleanDecoder,
    R2dbcBasicEncoding.ByteDecoder,
    R2dbcBasicEncoding.CharDecoder,
    R2dbcBasicEncoding.DoubleDecoder,
    R2dbcBasicEncoding.FloatDecoder,
    R2dbcBasicEncoding.IntDecoder,
    R2dbcBasicEncoding.LongDecoder,
    R2dbcBasicEncoding.ShortDecoder,
    R2dbcBasicEncoding.StringDecoder,
    R2dbcBasicEncoding.ByteArrayDecoder,

    R2dbcTimeEncoding.LocalDateDecoder,
    R2dbcTimeEncoding.LocalDateTimeDecoder,
    R2dbcTimeEncoding.LocalTimeDecoder,
    R2dbcTimeEncoding.InstantDecoder,
    R2dbcTimeEncoding.JLocalDateDecoder,
    R2dbcTimeEncoding.JLocalTimeDecoder,
    R2dbcTimeEncoding.JLocalDateTimeDecoder,
    R2dbcTimeEncoding.JZonedDateTimeDecoder,
    R2dbcTimeEncoding.JInstantDecoder,
    R2dbcTimeEncoding.JOffsetTimeDecoder,
    R2dbcTimeEncoding.JOffsetDateTimeDecoder,
    R2dbcTimeEncoding.JDateDecoder,
    R2dbcUuidEncodingNative.JUuidDecoder,

    R2dbcAdditionalEncoding.BigDecimalDecoder
  )
}
