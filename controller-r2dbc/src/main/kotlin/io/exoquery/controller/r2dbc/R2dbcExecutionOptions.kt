package io.exoquery.controller.r2dbc

import com.sun.jdi.connect.spi.Connection
import io.r2dbc.spi.Row
import io.r2dbc.spi.Statement

data class R2dbcExecutionOptions(
  val sessionTimeout: Int? = null,
  val fetchSize: Int? = null,
  val queryTimeout: Int? = null
) {
  companion object {
    fun Default() = R2dbcExecutionOptions()
  }
}
