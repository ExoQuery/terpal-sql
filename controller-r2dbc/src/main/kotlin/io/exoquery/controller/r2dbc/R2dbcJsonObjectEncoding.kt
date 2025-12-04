package io.exoquery.controller.r2dbc

import io.exoquery.controller.SqlJson

object R2dbcJsonObjectEncoding {
  private const val NA = 0
  val SqlJsonEncoder: R2dbcEncoderAny<SqlJson> = R2dbcEncoderAny(NA, SqlJson::class) { ctx, v, i -> ctx.stmt.bind(i, io.r2dbc.postgresql.codec.Json.of(v.value)) }
  val SqlJsonDecoder: R2dbcDecoderAny<SqlJson> = R2dbcDecoderAny(SqlJson::class) { ctx, i -> SqlJson(ctx.row.get(i, io.r2dbc.postgresql.codec.Json::class.java).asString()) }

  val encoders: Set<R2dbcEncoderAny<*>> = setOf(SqlJsonEncoder)
  val decoders: Set<R2dbcDecoderAny<*>> = setOf(SqlJsonDecoder)
}

object R2dbcJsonTextEncoding {
  private const val NA = 0
  val SqlJsonEncoder: R2dbcEncoderAny<SqlJson> = R2dbcEncoderAny(NA, SqlJson::class) { ctx, v, i -> ctx.stmt.bind(i, v.value) }
  val SqlJsonDecoder: R2dbcDecoderAny<SqlJson> = R2dbcDecoderAny(SqlJson::class) { ctx, i -> SqlJson(ctx.row.get(i, String::class.java)) }

  val encoders: Set<R2dbcEncoderAny<*>> = setOf(SqlJsonEncoder)
  val decoders: Set<R2dbcDecoderAny<*>> = setOf(SqlJsonDecoder)
}
