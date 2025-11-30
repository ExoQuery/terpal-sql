package io.exoquery.controller

import kotlin.reflect.KClass

open class EncoderAny<T: Any, TypeId: Any, Session, Stmt> @PublishedApi internal constructor(
  open val dataType: TypeId,
  open override val type: KClass<T>,
  /**
   * The original type of this encoder at the time of its initial creation.
   * This field remains unchanged through transformations like [contramap] and [transformFrom].
   * Used in R2DBC's bindNull operation where the driver requires the actual Java class
   * of the original type rather than a JDBC type integer.
   */
  open val originalType: KClass<*>,
  open val setNull: (Int, Stmt, TypeId) -> Unit,
  open val f: (EncodingContext<Session, Stmt>, T, Int) -> Unit
): SqlEncoder<Session, Stmt, T>() {

  constructor(
    dataType: TypeId,
    type: KClass<T>,
    setNull: (Int, Stmt, TypeId) -> Unit,
    f: (EncodingContext<Session, Stmt>, T, Int) -> Unit
  ) : this(dataType, type, type, setNull, f)
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
          val jdbcTypeInfo = if (ctx.dbTypeIsRelevant) " (whose database-type: ${jdbcType})" else ""
          throw EncodingException("Error encoding ${type} value: $value at (${ctx.startingIndex.description}) index: $index${jdbcTypeInfo}", e)
        }
    }

  /**
   * Transforms this encoder into another encoder by applying the given function to the value before encoding it.
   * Alias for [contramap].
   */
  inline fun <reified R: Any> transformFrom(crossinline from: (R) -> T): EncoderAny<R, TypeId, Session, Stmt> =
    contramap(from)

  inline fun <reified R: Any> contramap(crossinline from: (R) -> T): EncoderAny<R, TypeId, Session, Stmt> =
    EncoderAny<R, TypeId, Session, Stmt>(this@EncoderAny.dataType, R::class, this@EncoderAny.originalType, this@EncoderAny.setNull) { ctx, value, i -> this.f(ctx, from(value), i) }
}
