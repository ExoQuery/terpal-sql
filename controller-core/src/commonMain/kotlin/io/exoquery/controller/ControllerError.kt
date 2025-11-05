package io.exoquery.controller

class ControllerError(message: String, cause: Throwable? = null) : Exception(message, cause)
