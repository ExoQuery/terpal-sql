package io.exoquery.controller.android

import java.util.*

internal fun julianDateToCalendar(jd: Double?, cal: Calendar): Calendar? {
  if (jd == null) {
    return null
  }

  val yyyy: Int
  val dd: Int
  val mm: Int
  val hh: Int
  val mn: Int
  val ss: Int
  val ms: Int
  val A: Int

  val w = jd + 0.5
  val Z = w.toInt()
  val F = w - Z

  if (Z < 2299161) {
    A = Z
  } else {
    val alpha = ((Z - 1867216.25) / 36524.25).toInt()
    A = Z + 1 + alpha - (alpha / 4.0).toInt()
  }

  val B = A + 1524
  val C = ((B - 122.1) / 365.25).toInt()
  val D = (365.25 * C).toInt()
  val E = ((B - D) / 30.6001).toInt()

  //  month
  mm = E - (if (E < 13.5) 1 else 13)

  // year
  yyyy = C - (if (mm > 2.5) 4716 else 4715)

  // Day
  val jjd = B - D - (30.6001 * E).toInt() + F
  dd = jjd.toInt()

  // Hour
  val hhd = jjd - dd
  hh = (24 * hhd).toInt()

  // Minutes
  val mnd = (24 * hhd) - hh
  mn = (60 * mnd).toInt()

  // Seconds
  val ssd = (60 * mnd) - mn
  ss = (60 * ssd).toInt()

  // Milliseconds
  val msd = (60 * ssd) - ss
  ms = (1000 * msd).toInt()

  cal.set(yyyy, mm - 1, dd, hh, mn, ss)
  cal.set(Calendar.MILLISECOND, ms)

  if (yyyy < 1) {
    cal.set(Calendar.ERA, GregorianCalendar.BC)
    cal.set(Calendar.YEAR, -(yyyy - 1))
  }

  return cal
}
