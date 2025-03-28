package io.exoquery.sql

data class EncodingException(val msg: String, val errorCause: Throwable? = null): Exception(msg.toString(), errorCause) {
  override fun toString(): String = msg
}
