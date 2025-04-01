package io.exoquery.controller

data class ExecutionOptions(
  val sessionTimeout: Int? = null,
  val fetchSize: Int? = null,
  val queryTimeout: Int? = null
)
