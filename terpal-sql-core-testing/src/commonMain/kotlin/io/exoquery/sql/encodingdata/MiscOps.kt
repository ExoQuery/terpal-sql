package io.exoquery.sql.encodingdata

import kotlin.test.assertEquals

infix fun <T> T.shouldBe(other: T) = assertEquals(other, this)

infix fun <A : Any> A.shouldBeEqual(expected: A): A {
  assertEquals(this, expected)
  return this
}

public infix fun <A> A?.shouldBeEqualNullable(expected: A?) =
  assertEquals(expected, this)


infix fun String?.shouldBeEqualEmptyNullable(expected: String?) {
  val actualOrEmpty = this ?: ""
  val expectedOrEmpty = expected ?: ""
  assertEquals(expectedOrEmpty, actualOrEmpty)
}

infix fun ByteArray?.shouldBeEqualEmptyNullable(expected: ByteArray?) {
  val actualOrEmpty = this ?: byteArrayOf()
  val expectedOrEmpty = expected ?: byteArrayOf()
  assertEquals(expectedOrEmpty.toList(), actualOrEmpty.toList())
}

infix fun SerializeableTestType?.shouldBeEqualEmptyNullable(expected: SerializeableTestType?) {
  val actualOrEmpty = this ?: SerializeableTestType("")
  val expectedOrEmpty = expected ?: SerializeableTestType("")
  assertEquals(expectedOrEmpty, actualOrEmpty)
}