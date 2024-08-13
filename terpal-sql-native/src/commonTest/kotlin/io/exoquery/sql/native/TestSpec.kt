package io.exoquery.sql.native

import kotlin.test.Test

class TestSpec {

  @Test
  fun `one plus one blah + 1`() {
    val i = kotlinx.datetime.Instant.fromEpochSeconds(100, 0)
    val s = i.toString()
    println(s)

    val ii = kotlinx.datetime.Instant.parse(s)
    println(ii)
    println(ii == i)
  }
}