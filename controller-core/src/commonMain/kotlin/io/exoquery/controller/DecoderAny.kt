package io.exoquery.controller

import kotlin.jvm.JvmInline
import kotlin.reflect.KClass

open class DecoderAny<T: Any, Session, Row> @PublishedApi internal constructor(
  open override val type: KClass<T>,
  /**
   * The original type of this decoder at the time of its initial creation.
   * This field remains unchanged through transformations like [map] and [transformInto].
   * Used in R2DBC's get operation where the driver requires the actual Java class
   * of the original type for proper type conversion.
   */
  open val originalType: KClass<*>,
  open val isNull: (Int, Row) -> Boolean,
  open val f: (DecodingContext<Session, Row>, Int) -> T?,
): SqlDecoder<Session, Row, T>() {

  constructor(
    type: KClass<T>,
    isNull: (Int, Row) -> Boolean,
    f: (DecodingContext<Session, Row>, Int) -> T?
  ) : this(type, type, isNull, f)
  override fun isNullable(): Boolean = false
  override fun decode(ctx: DecodingContext<Session, Row>, index: Int): T {
    val value =
      try {
        f(ctx, index)
      } catch (ex: Exception) {
        val msg =
          "Error decoding column at index $index for type ${type.simpleName}" +
            (ctx.columnInfoSafe(index)?.let { " (${it.name}:${it.type})" } ?: "")
        throw ControllerError.DecodingError(msg, ex)
      }
    if (value == null && !isNullable()) {
      val msg =
        "Got null value for non-nullable column of type ${type.simpleName} at index $index" +
          (ctx.columnInfoSafe(index)?.let { " (${it.name}:${it.type})" } ?: "")

      throw NullPointerException(msg)
    }
    return value as T
  }

  /**
   * Transforms this decoder into another decoder by applying the given function to the decoded value.
   * Alias for [map].
   */
  inline fun <reified R: Any> transformInto(crossinline into: MapContext<Session, Row>.(T) -> R): DecoderAny<R, Session, Row> =
    map(into)

  @JvmInline
  final value class MapContext<Session, Row>(val ctx: DecodingContext<Session, Row>)

  inline fun <reified R: Any> map(crossinline into: MapContext<Session, Row>.(T) -> R): DecoderAny<R, Session, Row> =
    DecoderAny<R, Session, Row>(R::class, this@DecoderAny.originalType, isNull) { ctx, index -> into(MapContext(ctx), this.decode(ctx, index)) }

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
