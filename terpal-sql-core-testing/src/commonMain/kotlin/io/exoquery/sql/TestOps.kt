package io.exoquery.sql

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.delay
import kotlin.random.nextInt
import kotlin.test.fail

suspend fun waitRandom(lower: Int, upper: Int) {
  val r = IntRange(lower, upper)
  val randNum = kotlin.random.Random.nextInt(r)
  delay(randNum.toLong())
}

fun SqlDriver.executeSimple(sql: String) = execute(null, sql, 0)

inline fun <reified T : Throwable> shouldThrow(block: () -> Any?): T {
  val expectedExceptionClass = T::class
  val thrownThrowable = try {
    block()
    null  // Can't throw failure here directly, as it would be caught by the catch clause, and it's an AssertionError, which is a special case
  } catch (thrown: Throwable) {
    thrown
  }

  return when (thrownThrowable) {
    null -> fail("Expected exception ${expectedExceptionClass.simpleName} but no exception was thrown.")
    is T -> thrownThrowable               // This should be before `is AssertionError`. If the user is purposefully trying to verify `shouldThrow<AssertionError>{}` this will take priority
    is AssertionError -> throw thrownThrowable
    else -> throw fail(
      "Expected exception ${expectedExceptionClass.simpleName} but a ${thrownThrowable::class.simpleName} was thrown instead.",
      thrownThrowable
    )
  }
}