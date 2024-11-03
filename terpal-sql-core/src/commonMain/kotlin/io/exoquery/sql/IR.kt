package io.exoquery.sql

sealed interface IR {
  data class Part(val value: String): IR {
    companion object {
      val Empty = Part("")
    }
  }

  sealed interface Var: IR
  data class Param(val value: io.exoquery.sql.Param<*>): Var
  data class Params(val value: io.exoquery.sql.Params<*>): Var
  data class Splice(val parts: List<Part>, val params: List<Var>): Var {
    fun flatten(): Splice {
      // rewrite this. First put everything into a single array, then walk through that and splice
      // the adjacent parts together, then write it all back into two different arrays
      val accum = mutableListOf<IR>()

      val partsIter = parts.iterator()
      val paramsIter = params.iterator()


      while(partsIter.hasNext()) {
        accum.add(partsIter.next())

        if (paramsIter.hasNext()) {
          when (val nextParam = paramsIter.next()) {
            is Param -> {
              accum += nextParam
            }

            is Params -> {
              val individualParams: List<IR> =
                nextParam.value.flatten().map { Param(it) }.intersperse(Part(", "))

              // TODO DOUBLE CHECK that the parts on the edges are interplaced properly
              accum += IR.Part("(")
              accum.addAll(individualParams)
              accum += IR.Part(")")
            }

            // recursively flatten the inner splice, then grab out it's contents
            // for example, Splice("--$A-${Splice("_$B_$C_")}-$D--) at the point of reaching ${Splice(...)}
            // should be: (parts:["--", "-"], params:[A].
            // When the the splice is complete it should be: (parts:["--", ("-" + "_"), "_", "_"], params:[A, B, C])
            is Splice -> {
              // TODO DOUBLE CHECK what happnes if nextParam is also a splice i.e. a splice within a splice
              //             NEED TO TEST THE RECURSIVE CASE
              val flatInterlaced =
                nextParam.flatten()
                  .let { it.parts.interlace(it.params) }

              accum.addAll(flatInterlaced)
            }
          }
        }
      }

      val (partsAccum, paramsAccum) = accum.joinParts()

      return IR.Splice(partsAccum, paramsAccum)
    }
  }
}

fun <T> List<T>.intersperse(separator: T): List<T> {
  if (isEmpty()) return this
  return listOf(first()) + this.drop(1).flatMap { listOf(separator, it) }
}

fun <T> List<T>.interlace(other: List<T>): List<T> {
  val result = mutableListOf<T>()
  val thisIter = this.iterator()
  val otherIter = other.iterator()

  while (thisIter.hasNext() || otherIter.hasNext()) {
    if (thisIter.hasNext()) result.add(thisIter.next())
    if (otherIter.hasNext()) result.add(otherIter.next())
  }

  return result
}

fun List<IR>.joinParts(): Pair<List<IR.Part>, List<IR.Param>> {
  val mut = this.toMutableList()
  var lastPart: IR.Part? = null
  val partsAccum = mutableListOf<IR.Part>()
  val paramsAccum = mutableListOf<IR.Param>()

  fun addOrSetLastPart(part: IR.Part) {
    if (lastPart == null) {
      lastPart = part
    } else {
      // only need to do ?: becuase smart-cast doesn't work which is odd!
      lastPart = IR.Part((lastPart?.value ?: "") + part.value)
    }
  }
  fun clearLastPart() {
    if (lastPart != null)
      partsAccum.add(lastPart!!)
    lastPart = null
  }

  while (mut.isNotEmpty()) {
    when (val next = mut.removeFirst()) {
      is IR.Part -> {
        addOrSetLastPart(next)
      }
      is IR.Param -> {
        clearLastPart()
        paramsAccum += next
      }

      is IR.Splice -> throw IllegalStateException("Splice should not be in the list of parts, they should have been flattened before.")
      is IR.Params -> throw IllegalStateException("Params should not be in the list of parts, they should have been expanded before.")
    }
  }

  // if at the end of it we still have a part that we haven't added to the accum add it now
  clearLastPart()

  return partsAccum to paramsAccum
}