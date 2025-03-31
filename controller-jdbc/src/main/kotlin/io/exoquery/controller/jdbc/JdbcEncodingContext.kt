package io.exoquery.controller.jdbc

import io.exoquery.controller.DecodingContext
import io.exoquery.controller.EncodingContext
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.time.ZoneOffset
import java.util.*

typealias JdbcEncodingContext = EncodingContext<Connection, PreparedStatement>
typealias JdbcDecodingContext = DecodingContext<Connection, ResultSet>
