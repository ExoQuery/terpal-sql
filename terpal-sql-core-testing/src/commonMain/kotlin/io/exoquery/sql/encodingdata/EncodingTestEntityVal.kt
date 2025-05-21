package io.exoquery.sql.encodingdata

import io.exoquery.controller.ControllerAction
import io.exoquery.controller.util.SerializerFrom
import io.exoquery.sql.Param
import io.exoquery.sql.Sql
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
value class StringVal(val value: String)
@JvmInline
@Serializable
value class IntVal(val value: Int)
@JvmInline
@Serializable
value class LongVal(val value: Long)
@JvmInline
@Serializable
value class DoubleVal(val value: Double)
@JvmInline
@Serializable
value class FloatVal(val value: Float)
@JvmInline
@Serializable
value class BooleanVal(val value: Boolean)
@JvmInline
@Serializable
value class ShortVal(val value: Short)
@JvmInline
@Serializable
value class ByteVal(val value: Byte)

fun insert(e: EncodingTestEntityVal): ControllerAction {
  fun wrap(value: SerializeableTestType?): Param<SerializeableTestType> = Param.withSer(value, SerializeableTestType.serializer())
  val wrapString = Param.withSer(e.stringMan, StringVal.serializer())
  val wrapBoolean = Param.withSer(e.booleanMan, BooleanVal.serializer())
  val wrapByte = Param.withSer(e.byteMan, ByteVal.serializer())
  val wrapShort = Param.withSer(e.shortMan, ShortVal.serializer())
  val wrapInt = Param.withSer(e.intMan, IntVal.serializer())
  val wrapLong = Param.withSer(e.long, LongVal.serializer())
  val wrapFloat = Param.withSer(e.floatMan, FloatVal.serializer())
  val wrapDouble = Param.withSer(e.double, DoubleVal.serializer())
  val wrapByteArray = e.byteArrayMan
  val wrapCustom = Param.withSer(e.customMan, SerializeableTestType.serializer())
  val wrapStringOpt = Param.withSer(e.stringOpt, StringVal.serializer())
  val wrapBooleanOpt = Param.withSer(e.booleanOpt, BooleanVal.serializer())
  val wrapByteOpt = Param.withSer(e.byteOpt, ByteVal.serializer())
  val wrapShortOpt = Param.withSer(e.shortOpt, ShortVal.serializer())
  val wrapIntOpt = Param.withSer(e.intOpt, IntVal.serializer())
  val wrapLongOpt = Param.withSer(e.longOpt, LongVal.serializer())
  val wrapFloatOpt = Param.withSer(e.floatOpt, FloatVal.serializer())
  val wrapDoubleOpt = Param.withSer(e.doubleOpt, DoubleVal.serializer())
  val wrapByteArrayOpt = e.byteArrayOpt
  val wrapCustomOpt = Param.withSer(e.customOpt, SerializeableTestType.serializer())
  return Sql("INSERT INTO EncodingTestEntity VALUES (${wrapString}, ${wrapBoolean}, ${wrapByte}, ${wrapShort}, ${wrapInt}, ${wrapLong}, ${wrapFloat}, ${wrapDouble}, ${wrapByteArray}, ${wrapCustom}, ${wrapStringOpt}, ${wrapBooleanOpt}, ${wrapByteOpt}, ${wrapShortOpt}, ${wrapIntOpt}, ${wrapLongOpt}, ${wrapFloatOpt}, ${wrapDoubleOpt}, ${wrapByteArrayOpt}, ${wrapCustomOpt})").action()
}

fun verify(e1: EncodingTestEntityVal, e2: EncodingTestEntityVal, oracleStrings: Boolean = false) {
  e1.stringMan.value shouldBeEqualEmptyNullable e2.stringMan.value
  e1.booleanMan shouldBeEqual e2.booleanMan
  e1.byteMan shouldBeEqual e2.byteMan
  e1.shortMan shouldBeEqual e2.shortMan
  e1.intMan shouldBeEqual e2.intMan
  e1.long shouldBeEqual e2.long
  e1.floatMan shouldBeEqual e2.floatMan
  e1.double shouldBeEqual e2.double
  e1.byteArrayMan.toList() shouldBeEqual e2.byteArrayMan.toList()
  e1.customMan shouldBeEqual e2.customMan

  if (!oracleStrings) e1.stringOpt shouldBeEqualNullable e2.stringOpt
  else e1.stringOpt?.value shouldBeEqualEmptyNullable(e2.stringOpt?.value)
  e1.booleanOpt shouldBeEqualNullable e2.booleanOpt
  e1.byteOpt shouldBeEqualNullable e2.byteOpt
  e1.shortOpt shouldBeEqualNullable e2.shortOpt
  e1.intOpt shouldBeEqualNullable e2.intOpt
  e1.longOpt shouldBeEqualNullable e2.longOpt
  e1.floatOpt shouldBeEqualNullable(e2.floatOpt)
  e1.doubleOpt shouldBeEqualNullable(e2.doubleOpt)
  (e1.byteArrayOpt?.toList() ?: emptyList()) shouldBeEqualNullable (e2.byteArrayOpt?.toList() ?: emptyList())
  e1.customOpt shouldBeEqualNullable e2.customOpt
}

@Serializable
data class EncodingTestEntityVal(
  val stringMan: StringVal,
  val booleanMan: BooleanVal,
  val byteMan: ByteVal,
  val shortMan: ShortVal,
  val intMan: IntVal,
  val long: LongVal,
  val floatMan: FloatVal,
  val double: DoubleVal,
  val byteArrayMan: ByteArray,
  val customMan: SerializeableTestType,
  val stringOpt: StringVal?,
  val booleanOpt: BooleanVal?,
  val byteOpt: ByteVal?,
  val shortOpt: ShortVal?,
  val intOpt: IntVal?,
  val longOpt: LongVal?,
  val floatOpt: FloatVal?,
  val doubleOpt: DoubleVal?,
  val byteArrayOpt: ByteArray?,
  val customOpt: SerializeableTestType?
) {
  companion object {
    val regular =
      EncodingTestEntityVal(
        stringMan = StringVal("s"),
        booleanMan = BooleanVal(true),
        byteMan = ByteVal(11.toByte()),
        shortMan = ShortVal(23.toShort()),
        intMan = IntVal(33),
        long = LongVal(431L),
        floatMan = FloatVal(34.4f),
        double = DoubleVal(42.0),
        byteArrayMan = byteArrayOf(1.toByte(), 2.toByte()),
        customMan = SerializeableTestType("s"),
        stringOpt = StringVal("s"),
        booleanOpt = BooleanVal(true),
        byteOpt = ByteVal(11.toByte()),
        shortOpt = ShortVal(23.toShort()),
        intOpt = IntVal(33),
        longOpt = LongVal(431L),
        floatOpt = FloatVal(34.4f),
        doubleOpt = DoubleVal(42.0),
        byteArrayOpt = byteArrayOf(1.toByte(), 2.toByte()),
        customOpt = SerializeableTestType("s")
      )

    val empty =
      EncodingTestEntityVal(
        stringMan = StringVal(""),
        booleanMan = BooleanVal(false),
        byteMan = ByteVal(0.toByte()),
        shortMan = ShortVal(0.toShort()),
        intMan = IntVal(0),
        long = LongVal(0L),
        floatMan = FloatVal(0.0f),
        double = DoubleVal(0.0),
        byteArrayMan = byteArrayOf(),
        customMan = SerializeableTestType(""),
        stringOpt = null,
        booleanOpt = null,
        byteOpt = null,
        shortOpt = null,
        intOpt = null,
        longOpt = null,
        floatOpt = null,
        doubleOpt = null,
        byteArrayOpt = null,
        customOpt = null
      )
  }
}
