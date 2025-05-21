package io.exoquery.sql.encodingdata

import io.exoquery.controller.ControllerAction
import io.exoquery.controller.util.SerializerFrom
import io.exoquery.sql.Param
import io.exoquery.sql.Sql
import kotlinx.serialization.Serializable

object StringSer: SerializerFrom.String<StringImp>({ StringImp(it) }, { it.value }, "StringImp")
object IntSer: SerializerFrom.Int<IntImp>({ IntImp(it) }, { it.value }, "IntImp")
object LongSer: SerializerFrom.Long<LongImp>({ LongImp(it) }, { it.value }, "LongImp")
object DoubleSer: SerializerFrom.Double<DoubleImp>({ DoubleImp(it) }, { it.value }, "DoubleImp")
object FloatSer: SerializerFrom.Float<FloatImp>({ FloatImp(it) }, { it.value }, "FloatImp")
object BooleanSer: SerializerFrom.Boolean<BooleanImp>({ BooleanImp(it) }, { it.value }, "BooleanImp")
object ShortSer: SerializerFrom.Short<ShortImp>({ ShortImp(it) }, { it.value }, "ShortImp")
object ByteSer: SerializerFrom.Byte<ByteImp>({ ByteImp(it) }, { it.value }, "ByteImp")

@Serializable(with = StringSer::class)
data class StringImp(val value: String)
@Serializable(with = IntSer::class)
data class IntImp(val value: Int)
@Serializable(with = LongSer::class)
data class LongImp(val value: Long)
@Serializable(with = DoubleSer::class)
data class DoubleImp(val value: Double)
@Serializable(with = FloatSer::class)
data class FloatImp(val value: Float)
@Serializable(with = BooleanSer::class)
data class BooleanImp(val value: Boolean)
@Serializable(with = ShortSer::class)
data class ShortImp(val value: Short)
@Serializable(with = ByteSer::class)
data class ByteImp(val value: Byte)

fun insert(e: EncodingTestEntityImp): ControllerAction {
  fun wrap(value: SerializeableTestType?): Param<SerializeableTestType> = Param.withSer(value, SerializeableTestType.serializer())
  val wrapString = Param.withSer(e.stringMan, StringSer)
  val wrapBoolean = Param.withSer(e.booleanMan, BooleanSer)
  val wrapByte = Param.withSer(e.byteMan, ByteSer)
  val wrapShort = Param.withSer(e.shortMan, ShortSer)
  val wrapInt = Param.withSer(e.intMan, IntSer)
  val wrapLong = Param.withSer(e.long, LongSer)
  val wrapFloat = Param.withSer(e.floatMan, FloatSer)
  val wrapDouble = Param.withSer(e.double, DoubleSer)
  val wrapByteArray = e.byteArrayMan
  val wrapCustom = Param.withSer(e.customMan, SerializeableTestType.serializer())
  val wrapStringOpt = Param.withSer(e.stringOpt, StringSer)
  val wrapBooleanOpt = Param.withSer(e.booleanOpt, BooleanSer)
  val wrapByteOpt = Param.withSer(e.byteOpt, ByteSer)
  val wrapShortOpt = Param.withSer(e.shortOpt, ShortSer)
  val wrapIntOpt = Param.withSer(e.intOpt, IntSer)
  val wrapLongOpt = Param.withSer(e.longOpt, LongSer)
  val wrapFloatOpt = Param.withSer(e.floatOpt, FloatSer)
  val wrapDoubleOpt = Param.withSer(e.doubleOpt, DoubleSer)
  val wrapByteArrayOpt = e.byteArrayOpt
  val wrapCustomOpt = Param.withSer(e.customOpt, SerializeableTestType.serializer())
  return Sql("INSERT INTO EncodingTestEntity VALUES (${wrapString}, ${wrapBoolean}, ${wrapByte}, ${wrapShort}, ${wrapInt}, ${wrapLong}, ${wrapFloat}, ${wrapDouble}, ${wrapByteArray}, ${wrapCustom}, ${wrapStringOpt}, ${wrapBooleanOpt}, ${wrapByteOpt}, ${wrapShortOpt}, ${wrapIntOpt}, ${wrapLongOpt}, ${wrapFloatOpt}, ${wrapDoubleOpt}, ${wrapByteArrayOpt}, ${wrapCustomOpt})").action()
}

fun verify(e1: EncodingTestEntityImp, e2: EncodingTestEntityImp, oracleStrings: Boolean = false) {
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
data class EncodingTestEntityImp(
  val stringMan: StringImp,
  val booleanMan: BooleanImp,
  val byteMan: ByteImp,
  val shortMan: ShortImp,
  val intMan: IntImp,
  val long: LongImp,
  val floatMan: FloatImp,
  val double: DoubleImp,
  val byteArrayMan: ByteArray,
  val customMan: SerializeableTestType,
  val stringOpt: StringImp?,
  val booleanOpt: BooleanImp?,
  val byteOpt: ByteImp?,
  val shortOpt: ShortImp?,
  val intOpt: IntImp?,
  val longOpt: LongImp?,
  val floatOpt: FloatImp?,
  val doubleOpt: DoubleImp?,
  val byteArrayOpt: ByteArray?,
  val customOpt: SerializeableTestType?
) {
  companion object {
    val regular =
      EncodingTestEntityImp(
        stringMan = StringImp("s"),
        booleanMan = BooleanImp(true),
        byteMan = ByteImp(11.toByte()),
        shortMan = ShortImp(23.toShort()),
        intMan = IntImp(33),
        long = LongImp(431L),
        floatMan = FloatImp(34.4f),
        double = DoubleImp(42.0),
        byteArrayMan = byteArrayOf(1.toByte(), 2.toByte()),
        customMan = SerializeableTestType("s"),
        stringOpt = StringImp("s"),
        booleanOpt = BooleanImp(true),
        byteOpt = ByteImp(11.toByte()),
        shortOpt = ShortImp(23.toShort()),
        intOpt = IntImp(33),
        longOpt = LongImp(431L),
        floatOpt = FloatImp(34.4f),
        doubleOpt = DoubleImp(42.0),
        byteArrayOpt = byteArrayOf(1.toByte(), 2.toByte()),
        customOpt = SerializeableTestType("s")
      )

    val empty =
      EncodingTestEntityImp(
        stringMan = StringImp(""),
        booleanMan = BooleanImp(false),
        byteMan = ByteImp(0.toByte()),
        shortMan = ShortImp(0.toShort()),
        intMan = IntImp(0),
        long = LongImp(0L),
        floatMan = FloatImp(0.0f),
        double = DoubleImp(0.0),
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
