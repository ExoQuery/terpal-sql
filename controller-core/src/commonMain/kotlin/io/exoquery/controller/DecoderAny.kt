package io.exoquery.controller

import kotlin.reflect.KClass

open class DecoderAny<T: Any, Session, Row>(
  open override val type: KClass<T>,
  open val isNull: (Int, Row) -> Boolean,
  open val f: (DecodingContext<Session, Row>, Int) -> T?,
): SqlDecoder<Session, Row, T>() {
  override fun isNullable(): Boolean = false
  override fun decode(ctx: DecodingContext<Session, Row>, index: Int): T {
    val value = f(ctx, index)
    if (value == null && !isNullable()) {
      val msg =
        "Got null value for non-nullable column of type ${type.simpleName} at index $index" +
          (ctx.columnInfo(index-1)?.let { " (${it.name}:${it.type})" } ?: "")

      throw NullPointerException(msg)
    }
    return value as T
  }

  /**
   * Transforms this decoder into another decoder by applying the given function to the decoded value.
   * Alias for [map].
   */
  inline fun <reified R: Any> transformInto(crossinline into: (T) -> R): DecoderAny<R, Session, Row> =
    map(into)

  inline fun <reified R: Any> map(crossinline into: (T) -> R): DecoderAny<R, Session, Row> =
    DecoderAny<R, Session, Row>(R::class, isNull) { ctx, index -> into(this.decode(ctx, index)) }

  override fun asNullable(): SqlDecoder<Session, Row, T?> =
    object: SqlDecoder<Session, Row, T?>() {
      override fun asNullable(): SqlDecoder<Session, Row, T?> = this
      override fun isNullable(): Boolean = true
      override val type = this@DecoderAny.type
      override fun decode(ctx: DecodingContext<Session, Row>, index: Int): T? =
        if (isNull(index, ctx.row))
          null
        else
          this@DecoderAny.decode(ctx, index)
    }
}
