@file:OptIn(TerpalSqlInternal::class)

package io.exoquery.sql

import io.exoquery.controller.TerpalSqlInternal
import kotlin.jvm.JvmInline

/**
 * A more restrictive version variation of the [IR.Splice] that only allows for a flat list of [IR.Leaf]s.
 * I.e. no nested splices or Params instances.
 */
@TerpalSqlInternal
@JvmInline
value class FlatSplice(val list: List<IR.Leaf>) {
  fun toSplice() =
    IR.Splice(
      list.filterIsInstance<IR.Part>(),
      list.filterIsInstance<IR.Var>()
    )
}

@TerpalSqlInternal
sealed interface IR {
  sealed interface Var: IR
  sealed interface Leaf: IR

  data class Part(val value: String): IR, Leaf {
    companion object {
      val Empty = Part("")
    }
  }

  data class Param(val value: io.exoquery.sql.Param<*>): Var, Leaf
  data class Params(val value: io.exoquery.sql.Params<*>): Var
  data class Splice(val parts: List<Part>, val params: List<Var>): Var {
    fun flatten() = flattenToLeaves().toSplice()

    fun flattenToLeaves(): FlatSplice {
      // rewrite this. First put everything into a single array, then walk through that and splice
      // the adjacent parts together, then write it all back into two different arrays
      val accum = mutableListOf<IR.Leaf>()

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
              if (nextParam.value.values.isNotEmpty()) {
                val individualParams = nextParam.value.toParamList().map { Param(it) }.intersperse(Part(", "))
                accum += IR.Part("(")
                accum.addAll(individualParams)
                accum += IR.Part(")")
              } else {
                // since `IN ()` is invalid SQL, we need to add a placeholder value
                // the actual empty list should be ignored (i.e. not added to the accumulation)
                accum += IR.Part("(null)")
              }
            }

            // recursively flatten the inner splice, then grab out it's contents
            // for example, Splice("--$A-${Splice("_$B_$C_")}-$D--) at the point of reaching ${Splice(...)}
            // should be: (parts:["--", "-"], params:[A].
            // When the the splice is complete it should be: (parts:["--", ("-" + "_"), "_", "_"], params:[A, B, C])
            is Splice -> {
              // TODO DOUBLE CHECK what happnes if nextParam is also a splice i.e. a splice within a splice
              //             NEED TO TEST THE RECURSIVE CASE
              val flatInterlaced = nextParam.flattenToLeaves()
              accum.addAll(flatInterlaced.list)
            }
          }
        }
      }

      val newLeaves = accum.joinParts()
      return FlatSplice(newLeaves)
    }
  }
}

fun <T> List<T>.intersperse(separator: T): List<T> {
  if (isEmpty()) return this
  return listOf(first()) + this.drop(1).flatMap { listOf(separator, it) }
}

//fun <T> List<T>.interlace(other: List<T>): List<T> {
//  val result = mutableListOf<T>()
//  val thisIter = this.iterator()
//  val otherIter = other.iterator()
//
//  while (thisIter.hasNext() || otherIter.hasNext()) {
//    if (thisIter.hasNext()) result.add(thisIter.next())
//    if (otherIter.hasNext()) result.add(otherIter.next())
//  }
//
//  return result
//}

fun List<IR.Leaf>.joinParts(): List<IR.Leaf> {
  val mut = this.toMutableList()
  var lastPart: IR.Part? = null
  val accum = mutableListOf<IR.Leaf>()

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
      accum.add(lastPart!!)
    lastPart = null
  }

  while (mut.isNotEmpty()) {
    when (val next = mut.removeFirst()) {
      is IR.Part -> {
        addOrSetLastPart(next)
      }
      is IR.Param -> {
        clearLastPart()
        accum += next
      }
    }
  }

  // if at the end of it we still have a part that we haven't added to the accum add it now
  clearLastPart()

  return accum
}