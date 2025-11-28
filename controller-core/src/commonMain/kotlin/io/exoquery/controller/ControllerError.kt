package io.exoquery.controller

open class ControllerError(message: String, cause: Throwable? = null) : Exception(message, cause) {
  class DecodingError(message: String, cause: Throwable? = null) : ControllerError(message, cause)
}
