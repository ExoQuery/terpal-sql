package io.exoquery.controller.jdbc

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * Jdbc execution options are designed to provide maximum flexibility so that users can reach down into the JDBC stack
 * and set settings as appropriate. The first three options are for regular users to be able to set timings.
 * The last three are for power-users in order to control the preparation of the connection, statement and result set.
 * The queryTimeout and fetchSize settings are applied on the JDBC PreparedStatement when it is created.
 * The sessionTimeout is applied on the JDBC Connection when it is created. The sessionTimeout, queryTimeout and fetchSize
 * will be applied to a connection even when prepareConnection and prepareStatement are used so be sure to set the first three
 * to `null` if you are doing the work yourself via the latter prepareConnection and prepareStatement options.
 *
 * Note that if you are inside of a transaction
 * it will not create a new connection for every statement, usually only the first in the `transaction` block one will create the statement
 * and subsequent statements will reuse it. For example:
 * ```
 * ctx.transaction {
 *   insert(joe).run() // <- only this will create a new connection (applying the sessionTimeout and prepareConnection)
 *   insert(jim).run()
 * }
 * ```
 */
data class JdbcExecutionOptions(
  val sessionTimeout: Int? = null,
  val fetchSize: Int? = null,
  val queryTimeout: Int? = null,
  val prepareConnection: (Connection) -> Connection = { it },
  val prepareStatement: (PreparedStatement) -> PreparedStatement = { it },
  val prepareResult: (ResultSet) -> ResultSet = { it }
) {
  companion object {
    fun Default() = JdbcExecutionOptions()
  }
}
