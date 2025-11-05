package io.exoquery.controller.r2dbc

import io.exoquery.controller.SqlEncoding
import io.r2dbc.spi.Connection
import io.r2dbc.spi.Row
import io.r2dbc.spi.Statement

typealias R2dbcSqlEncoding = SqlEncoding<Connection, Statement, Row>
