package io.exoquery.sql

import kotlin.reflect.KClass

open class DecoderAny<T: Any, Session, Row>(
  open override val type: KClass<T>,
  open val isNull: (Int, Row) -> Boolean,
  open val f: (DecodingContext<Session, Row>, Int) -> T,
): SqlDecoder<Session, Row, T>() {
  override fun decode(ctx: DecodingContext<Session, Row>, index: Int): T =
    f(ctx, index)

  inline fun <reified R: Any> map(crossinline f: (T) -> R): DecoderAny<R, Session, Row> =
    DecoderAny<R, Session, Row>(R::class, isNull) { ctx, index -> f(this.decode(ctx, index)) }

  override fun asNullable(): SqlDecoder<Session, Row, T?> =
    object: SqlDecoder<Session, Row, T?>() {
      override fun asNullable(): SqlDecoder<Session, Row, T?> = this
      override val type = this@DecoderAny.type
      override fun decode(ctx: DecodingContext<Session, Row>, index: Int): T? =
        if (isNull(index, ctx.row))
          null
        else
          this@DecoderAny.decode(ctx, index)
    }
}
