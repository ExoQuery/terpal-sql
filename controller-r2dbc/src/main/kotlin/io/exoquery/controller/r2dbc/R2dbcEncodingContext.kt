package io.exoquery.controller.r2dbc

import io.exoquery.controller.DecodingContext
import io.exoquery.controller.EncodingContext
import io.r2dbc.spi.Connection
import io.r2dbc.spi.Row
import io.r2dbc.spi.Statement

typealias R2dbcEncodingContext = EncodingContext<Connection, Statement>
typealias R2dbcDecodingContext = DecodingContext<Connection, Row>
