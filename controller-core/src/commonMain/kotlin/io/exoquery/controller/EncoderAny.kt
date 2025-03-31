package io.exoquery.controller

import kotlin.reflect.KClass

open class EncoderAny<T: Any, TypeId: Any, Session, Stmt>(
  open val dataType: TypeId,
  open override val type: KClass<T>,
  open val setNull: (Int, Stmt, TypeId) -> Unit,
  open val f: (EncodingContext<Session, Stmt>, T, Int) -> Unit
): SqlEncoder<Session, Stmt, T>() {
  override fun encode(ctx: EncodingContext<Session, Stmt>, value: T, index: Int) =
    f(ctx, value, index)

  override fun asNullable(): SqlEncoder<Session, Stmt, T?> =
    object: SqlEncoder<Session, Stmt, T?>() {
      override val type = this@EncoderAny.type
      val jdbcType = this@EncoderAny.dataType
      override fun asNullable(): SqlEncoder<Session, Stmt, T?> = this

      override fun encode(ctx: EncodingContext<Session, Stmt>, value: T?, index: Int) =
        try {
          if (value != null)
            this@EncoderAny.encode(ctx, value, index)
          else
            setNull(index, ctx.stmt, jdbcType)
        } catch (e: Throwable) {
          throw EncodingException("Error encoding ${type} value: $value at index: $index (whose jdbc-type: ${jdbcType})", e)
        }
    }

  inline fun <reified R: Any> contramap(crossinline f: (R) -> T): EncoderAny<R, TypeId, Session, Stmt> =
    EncoderAny<R, TypeId, Session, Stmt>(this@EncoderAny.dataType, R::class, this@EncoderAny.setNull) { _, value, _ -> f(value) }
}
